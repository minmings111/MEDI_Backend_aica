package com.medi.backend.youtube.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.medi.backend.youtube.dto.YouTubeOAuthTokenDTO;
import com.medi.backend.youtube.mapper.YouTubeOAuthTokenMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Service
public class YouTubeOAuthService {

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Autowired
    private YouTubeOAuthTokenMapper tokenMapper;


    private static final List<String> SCOPES = Arrays.asList(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/youtube.force-ssl"
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

    public void handleCallback(String code, String baseRedirectUri, String state) {
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
            YouTubeOAuthTokenDTO dto = new YouTubeOAuthTokenDTO();
            dto.setUserId(userId);
            dto.setGoogleEmail("unknown@googleuser");
            dto.setAccessToken(accessToken);
            dto.setRefreshToken(refreshToken);
            dto.setAccessTokenExpiresAt(DF.format(expiresAt));
            dto.setTokenStatus("ACTIVE");
            tokenMapper.upsert(dto);
        } catch (Exception e) {
            throw new RuntimeException("OAuth callback handling failed", e);
        }
    }

    public String getValidAccessToken(Integer userId) {
        YouTubeOAuthTokenDTO token = tokenMapper.findByUserId(userId);
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


