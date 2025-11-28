package com.medi.backend.youtube.controller;

import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.YoutubeOAuthTokenDto;
import com.medi.backend.youtube.mapper.YoutubeOAuthTokenMapper;
import com.medi.backend.youtube.service.YoutubeOAuthService;
import com.medi.backend.youtube.service.YoutubeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * YouTube OAuth 연결 흐름을 담당하는 컨트롤러입니다.
 * 프런트는 connect → callback → token/status → (optional) sync 순서로 호출하여
 * 채널을 연동하고 동기화할 수 있습니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/youtube")
@RequiredArgsConstructor
public class YoutubeOAuthController {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeService youtubeService;
    private final YoutubeOAuthTokenMapper tokenMapper;
    private final AuthUtil authUtil;
    private final FilterPreferenceService filterPreferenceService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * STEP 1: YouTube 채널 연결 시작
     *
     * 프런트에서 `/api/youtube/connect`로 이동하면, 현재 로그인한 사용자의 ID를 기반으로
     * Google OAuth 동의 화면 URL을 만들어 302 리다이렉트합니다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/connect")
    public ResponseEntity<Void> connect() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            log.warn("[YouTube] connect 요청 - 비로그인 사용자");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String callbackUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/youtube/oauth/callback")
            .build()
            .toUriString();

        String state = "youtube_connect_" + UUID.randomUUID();
        String authorizationUrl = youtubeOAuthService.buildAuthorizationUrl(userId, callbackUrl, state);

        log.info("[YouTube] connect 요청 - userId={}, redirect={}", userId, authorizationUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build();
    }

    /**
     * STEP 2: Google OAuth 콜백 처리
     *
     * Google이 전달한 authorization code를 토대로 access/refresh token을 저장하고
     * 필요하면 즉시 채널 동기화를 수행합니다. 이후 프런트엔드로 리다이렉트합니다.
     */
    @GetMapping("/oauth/callback")
    public void oauthCallback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse response
    ) throws IOException {
        String frontendBase = resolveFrontendBase();

        if (StringUtils.hasText(error)) {
            log.warn("[YouTube] OAuth 콜백 오류 - error={}, state={}", error, state);
            response.sendRedirect(frontendBase + "/dashboard?error=youtube_oauth_denied");
            return;
        }

        if (!StringUtils.hasText(code)) {
            log.error("[YouTube] OAuth 콜백 - code 누락, state={}", state);
            response.sendRedirect(frontendBase + "/dashboard?error=youtube_oauth_invalid_code");
            return;
        }

        try {
            String callbackUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/youtube/oauth/callback")
                .build()
                .toUriString();

            Integer userId = youtubeOAuthService.handleCallback(code, callbackUrl, state);
            log.info("[YouTube] OAuth 콜백 처리 완료 - userId={}", userId);

            // Optional: 콜백 직후 채널을 즉시 동기화하여 UX 향상
            Integer latestChannelId = null;
            if (userId != null) {
                log.info("🔄 [OAuth 콜백] 채널 동기화 시작: userId={}", userId);
                try {
                    List<com.medi.backend.youtube.dto.YoutubeChannelDto> syncedChannels = youtubeService.syncChannels(userId, true);
                    log.info("✅ [OAuth 콜백] 채널 동기화 완료: userId={}, 동기화된채널수={}개", 
                        userId, syncedChannels != null ? syncedChannels.size() : 0);
                    
                    if (syncedChannels != null && !syncedChannels.isEmpty()) {
                        // 가장 최근에 생성된 채널의 ID를 찾기 (createdAt 기준)
                        latestChannelId = syncedChannels.stream()
                            .filter(ch -> ch.getId() != null)
                            .max((ch1, ch2) -> {
                                if (ch1.getCreatedAt() == null && ch2.getCreatedAt() == null) return 0;
                                if (ch1.getCreatedAt() == null) return -1;
                                if (ch2.getCreatedAt() == null) return 1;
                                return ch1.getCreatedAt().compareTo(ch2.getCreatedAt());
                            })
                            .map(com.medi.backend.youtube.dto.YoutubeChannelDto::getId)
                            .orElse(syncedChannels.get(0).getId()); // createdAt이 없으면 첫 번째 채널 사용
                        
                        log.info("📌 [OAuth 콜백] 최신 채널 ID 추출: channelId={}", latestChannelId);
                    } else {
                        log.warn("⚠️ [OAuth 콜백] 동기화된 채널이 없습니다: userId={}", userId);
                    }
                } catch (Exception syncEx) {
                    log.error("❌ [OAuth 콜백] 채널 동기화 실패: userId={}, errorType={}, errorMessage={}", 
                        userId, syncEx.getClass().getSimpleName(), syncEx.getMessage(), syncEx);
                    // 예외를 다시 던지지 않음 (OAuth 콜백은 성공한 것으로 간주)
                }
            } else {
                log.warn("⚠️ [OAuth 콜백] userId가 null입니다. 채널 동기화를 건너뜁니다.");
            }

            // ✅ 채널 등록 완료 후 필터링 설정 확인 및 조건부 리다이렉트
            String redirectPath = determineRedirectPath(userId, latestChannelId);
            response.sendRedirect(frontendBase + redirectPath);
        } catch (Exception ex) {
            log.error("[YouTube] OAuth 콜백 처리 실패 - state={}, message={}", state, ex.getMessage(), ex);
            response.sendRedirect(frontendBase + "/dashboard?error=youtube_oauth_callback_failed");
        }
    }

    /**
     * STEP 3: 토큰 상태 확인
     *
     * 프런트는 이 API로 YouTube 연결 여부를 확인할 수 있습니다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/token/status")
    public ResponseEntity<Map<String, Object>> tokenStatus() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> body = new HashMap<>();
        YoutubeOAuthTokenDto token = tokenMapper.findByUserId(userId);

        if (token == null) {
            body.put("success", false);
            body.put("message", "YouTube OAuth 토큰이 없습니다.");
            body.put("requiresReconnect", true);
            return ResponseEntity.ok(body);
        }

        body.put("success", true);
        body.put("status", token.getTokenStatus());
        body.put("expiresAt", token.getAccessTokenExpiresAt());
        body.put("lastRefreshedAt", token.getLastRefreshedAt());
        body.put("lastUsedAt", token.getLastUsedAt());

        boolean requiresReconnect = !"ACTIVE".equalsIgnoreCase(token.getTokenStatus());
        try {
            LocalDateTime expiresAt = LocalDateTime.parse(token.getAccessTokenExpiresAt(), DF);
            body.put("expiresInMinutes", java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes());
            if (expiresAt.isBefore(LocalDateTime.now())) {
                requiresReconnect = true;
            }
        } catch (Exception ignore) {
            // 파싱 실패 시에는 클라이언트가 raw 값을 활용하도록 둔다.
        }
        body.put("requiresReconnect", requiresReconnect);

        return ResponseEntity.ok(body);
    }

    /**
     * 채널 등록 후 필터링 설정 확인 및 리다이렉트 경로 결정
     * 
     * @param userId 사용자 ID
     * @param channelId 새로 추가된 채널 ID (null 가능)
     * @return 리다이렉트 경로 (쿼리 파라미터 포함)
     */
    private String determineRedirectPath(Integer userId, Integer channelId) {
        if (userId == null) {
            log.warn("⚠️ [채널 등록 리다이렉트] userId가 null → 기본 대시보드로 이동");
            return "/dashboard?youtube=connected";
        }
        
        try {
            // ✅ 채널별 필터링 설정 확인 (channelId가 있으면 해당 채널, 없으면 전역)
            Optional<FilterPreferenceResponse> preference = 
                filterPreferenceService.getPreference(userId, channelId);
            
            if (preference.isEmpty() || 
                preference.get().getIsActive() == null || 
                !preference.get().getIsActive()) {
                // 필터링 설정이 없거나 비활성화된 경우 → 필터링 폼 페이지로 이동
                log.info("📝 [채널 등록 리다이렉트] 필터링 설정 없음 → 필터링 폼 페이지로 이동: userId={}, channelId={}", 
                    userId, channelId);
                
                if (channelId != null) {
                    return "/filter/setup?channelId=" + channelId + "&youtube=connected";
                } else {
                    return "/filter/setup?youtube=connected";
                }
            } else {
                // 필터링 설정이 있는 경우 → 대시보드로 이동
                log.info("✅ [채널 등록 리다이렉트] 필터링 설정 있음 → 대시보드로 이동: userId={}, channelId={}", 
                    userId, channelId);
                return "/dashboard?youtube=connected";
            }
            
        } catch (Exception e) {
            log.error("❌ [채널 등록 리다이렉트] 필터링 설정 확인 중 오류 발생: userId={}, channelId={}, error={}", 
                userId, channelId, e.getMessage(), e);
            // 오류 발생 시 기본적으로 대시보드로 이동
            return "/dashboard?youtube=connected";
        }
    }

    private String resolveFrontendBase() {
        return Optional.ofNullable(allowedOrigins)
            .filter(StringUtils::hasText)
            .map(origins -> origins.split(",")[0].trim())
            .filter(StringUtils::hasText)
            .orElse("http://localhost:3000");
    }
}

