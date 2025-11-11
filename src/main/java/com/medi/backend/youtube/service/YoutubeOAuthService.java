package com.medi.backend.youtube.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.medi.backend.youtube.dto.YoutubeOAuthTokenDto;
import com.medi.backend.youtube.mapper.YoutubeOAuthTokenMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class YoutubeOAuthService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Autowired
    private YoutubeOAuthTokenMapper tokenMapper;


    /**
     * YouTube 채널 등록 시 사용할 OAuth 스코프 목록입니다.
     *
     * <ul>
     *   <li>openid / email / profile: 기본 Google 계정 식별 및 프로필 정보</li>
     *   <li>https://www.googleapis.com/auth/youtube.readonly: 채널/영상 메타데이터 조회</li>
     *   <li>https://www.googleapis.com/auth/youtube.force-ssl: SSL이 강제된 YouTube API 접근(필요 권한)</li>
     * </ul>
     *
     * 일반 로그인에서는 application.yml의 scope 설정(기본 프로필)만 사용하고,
     * 채널 등록(connect 플로우) 시에만 아래 스코프가 포함된 동의 화면을 띄웁니다.
     */
    private static final List<String> SCOPES = Arrays.asList(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/youtube.readonly", //채널/영상 메타데이터 조회
            "https://www.googleapis.com/auth/youtube.force-ssl" //SSL이 강제된 YouTube API 접근(필요 권한)
    );

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String buildAuthorizationUrl(Integer userId, String baseRedirectUri, String state) {
        String redirectUri = baseRedirectUri; // e.g., http://localhost:8080/api/youtube/oauth/callback
        String finalState = StringUtils.hasText(state) ? state : "youtube_connect";
        finalState = finalState + ":userId=" + userId;

        GoogleAuthorizationCodeRequestUrl url = new GoogleAuthorizationCodeRequestUrl(
                clientId, redirectUri, SCOPES
        );
        return url.setState(finalState)
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    public Integer handleCallback(String code, String baseRedirectUri, String state) {
        try {
            String redirectUri = baseRedirectUri;
            GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId,
                    clientSecret,
                    code,
                    redirectUri
            ).execute();

            String accessToken = response.getAccessToken();
            String refreshToken = response.getRefreshToken();
            Integer expiresIn = response.getExpiresInSeconds() != null ? response.getExpiresInSeconds().intValue() : 3600;

            Integer userId = extractUserIdFromState(state);
            if (userId == null) {
                throw new IllegalStateException("Invalid state: no userId");
            }

            // 저장 준비
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);
            YoutubeOAuthTokenDto dto = new YoutubeOAuthTokenDto();
            dto.setUserId(userId);
            dto.setGoogleEmail("unknown@googleuser");
            dto.setAccessToken(accessToken);
            dto.setRefreshToken(refreshToken);
            dto.setAccessTokenExpiresAt(DF.format(expiresAt));
            dto.setTokenStatus("ACTIVE");
            tokenMapper.upsert(dto);
            return userId;
        } catch (Exception e) {
            throw new RuntimeException("OAuth callback handling failed", e);
        }
    }

    public String getValidAccessToken(Integer userId) {
        YoutubeOAuthTokenDto token = tokenMapper.findByUserId(userId);
        if (token == null) throw new IllegalStateException("YouTube token not found");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.parse(token.getAccessTokenExpiresAt(), DF);
        if (expiresAt.isAfter(now.plusMinutes(5))) {
            return token.getAccessToken();
        }
        // refresh
        String decryptedRefresh = token.getRefreshToken();
        try {
            GoogleTokenResponse refreshResp = new GoogleAuthorizationCodeTokenRequest(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientId, clientSecret,
                    decryptedRefresh, "")
                    .setGrantType("refresh_token")
                    .execute();
            String newAccess = refreshResp.getAccessToken();
            Integer expiresIn = refreshResp.getExpiresInSeconds() != null ? refreshResp.getExpiresInSeconds().intValue() : 3600;
            LocalDateTime newExpiresAt = LocalDateTime.now().plusSeconds(expiresIn);
            token.setAccessToken(newAccess);
            token.setAccessTokenExpiresAt(DF.format(newExpiresAt));
            tokenMapper.upsert(token);
            return newAccess;
        } catch (Exception ex) {
            tokenMapper.updateTokenStatus(token.getId(), "EXPIRED");
            throw new RuntimeException("Refresh token expired, reconnect required", ex);
        }
    }

    private Integer extractUserIdFromState(String state) {
        if (!StringUtils.hasText(state)) return null;
        int idx = state.indexOf("userId=");
        if (idx < 0) return null;
        try {
            return Integer.parseInt(state.substring(idx + 7));
        } catch (Exception e) {
            return null;
        }
    }
}


