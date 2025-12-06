package com.medi.backend.youtube.controller;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.dto.ChannelAgentRequestDto;
import com.medi.backend.youtube.dto.ChannelAnalysisResponseDto;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.service.ChannelThreatAnalysisService;
import com.medi.backend.youtube.service.DashboardTimePatternService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/youtube/analysis/channel")
@RequiredArgsConstructor
public class ChannelAnalysisController {

    private final ChannelThreatAnalysisService analysisService;
    private final DashboardTimePatternService dashboardTimePatternService;
    private final AuthUtil authUtil;
    private final YoutubeChannelMapper channelMapper;

    /**
     * 채널 소유권 검증
     * 
     * @param channelId 채널 ID
     * @param userId    사용자 ID
     * @throws RuntimeException 권한이 없거나 채널이 없는 경우
     */
    private void validateChannelOwnership(Integer channelId, Integer userId) {
        YoutubeChannelDto channel = channelMapper.findById(channelId);

        if (channel == null) {
            log.warn("🚫 [채널 없음] channelId={}", channelId);
            throw new RuntimeException("채널을 찾을 수 없습니다.");
        }

        if (!channel.getUserId().equals(userId)) {
            log.warn("🚫 [권한 없음] channelId={}, requestUserId={}, channelOwnerId={}",
                    channelId, userId, channel.getUserId());
            throw new RuntimeException("이 채널에 접근할 권한이 없습니다.");
        }

        log.info("✅ [권한 검증 통과] channelId={}, userId={}", channelId, userId);
    }

    /**
     * FastAPI Agent → Spring Boot 저장 API (채널 기준)
     * 
     * channelId는 두 가지 방식으로 전달 가능:
     * 1. 쿼리 파라미터로 DB의 channel_id (Integer) 직접 전달
     * 2. JSON에 youtube_channel_id 포함 시 자동으로 DB ID로 변환
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveFromAgent(
            @RequestParam(required = false) Integer channelId,
            @RequestBody String jsonPayload) {
        try {
            log.info("Agent 채널 분석 저장 요청: channelId={}", channelId);

            ChannelAgentRequestDto request = ChannelAgentRequestDto.builder()
                    .channelId(channelId) // null일 수 있음 (JSON에서 찾을 예정)
                    .jsonPayload(jsonPayload)
                    .build();

            Integer savedChannelId = analysisService.saveFromAgent(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "채널 분석 결과 저장 완료",
                    "channel_id", savedChannelId));

        } catch (Exception e) {
            log.error("Agent 채널 분석 저장 실패: channelId={}", channelId, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "저장 실패: " + e.getMessage(),
                            "channel_id", channelId != null ? channelId : "unknown"));
        }
    }

    /**
     * 프론트엔드 API 1: 채널 최신 보고서 메타데이터 조회
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{channelId}/metadata")
    public ResponseEntity<ChannelAnalysisResponseDto> getMetadata(
            @PathVariable Integer channelId) {
        try {
            // 1. 사용자 인증
            Integer userId = authUtil.getCurrentUserId();
            if (userId == null) {
                log.warn("🚫 [인증 실패] 로그인이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📡 [메타데이터 조회 요청] channelId={}, userId={}", channelId, userId);

            // 2. 채널 소유권 검증
            validateChannelOwnership(channelId, userId);

            // 3. 서비스 호출
            ChannelAnalysisResponseDto response = analysisService.getMetadata(channelId);

            log.info("✅ [메타데이터 조회 성공] channelId={}", channelId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ [메타데이터 조회 실패] channelId={}, error={}", channelId, e.getMessage());

            if (e.getMessage().contains("권한이 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            throw e;
        }
    }

    /**
     * 프론트엔드 API 2: 채널 최신 보고서 위협 인텔리전스 조회 (원본 JSON 그대로)
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{channelId}/threat-intelligence")
    public ResponseEntity<Map<String, Object>> getThreatIntelligence(
            @PathVariable Integer channelId) {
        try {
            // 1. 사용자 인증
            Integer userId = authUtil.getCurrentUserId();
            if (userId == null) {
                log.warn("🚫 [인증 실패] 로그인이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📡 [위협 인텔리전스 조회 요청] channelId={}, userId={}", channelId, userId);

            // 2. 채널 소유권 검증
            validateChannelOwnership(channelId, userId);

            // 3. 서비스 호출
            Map<String, Object> response = analysisService.getThreatIntelligence(channelId);

            log.info("✅ [위협 인텔리전스 조회 성공] channelId={}", channelId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ [위협 인텔리전스 조회 실패] channelId={}, error={}", channelId, e.getMessage());

            if (e.getMessage().contains("권한이 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            throw e;
        }
    }

    /**
     * 프론트엔드 API 3: 채널 최신 보고서 방어 전략 조회 (원본 JSON 그대로)
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{channelId}/defense-strategy")
    public ResponseEntity<Map<String, Object>> getDefenseStrategy(
            @PathVariable Integer channelId) {
        try {
            // 1. 사용자 인증
            Integer userId = authUtil.getCurrentUserId();
            if (userId == null) {
                log.warn("🚫 [인증 실패] 로그인이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📡 [방어 전략 조회 요청] channelId={}, userId={}", channelId, userId);

            // 2. 채널 소유권 검증
            validateChannelOwnership(channelId, userId);

            // 3. 서비스 호출
            Map<String, Object> response = analysisService.getDefenseStrategy(channelId);

            log.info("✅ [방어 전략 조회 성공] channelId={}", channelId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ [방어 전략 조회 실패] channelId={}, error={}", channelId, e.getMessage());

            if (e.getMessage().contains("권한이 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            throw e;
        }
    }

    /**
     * 프론트엔드 API 4: 채널 분석 히스토리 조회
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{channelId}/history")
    public ResponseEntity<List<Map<String, Object>>> getAnalysisHistory(
            @PathVariable Integer channelId,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        try {
            // 1. 사용자 인증
            Integer userId = authUtil.getCurrentUserId();
            if (userId == null) {
                log.warn("🚫 [인증 실패] 로그인이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📡 [히스토리 조회 요청] channelId={}, userId={}, limit={}", channelId, userId, limit);

            // 2. 채널 소유권 검증
            validateChannelOwnership(channelId, userId);

            // 3. 서비스 호출
            List<Map<String, Object>> history = analysisService.getAnalysisHistory(channelId, limit);

            log.info("✅ [히스토리 조회 성공] channelId={}, count={}", channelId, history.size());
            return ResponseEntity.ok(history);

        } catch (RuntimeException e) {
            log.error("❌ [히스토리 조회 실패] channelId={}, error={}", channelId, e.getMessage());

            if (e.getMessage().contains("권한이 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            throw e;
        }
    }

    /**
     * 프론트엔드 API 5: 대시보드 시간대별 악플 통계 조회
     * 
     * 용도: OverviewTab의 "악플 집중 시간대" 그래프 데이터
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{channelId}/dashboard/time-patterns")
    public ResponseEntity<Map<String, Object>> getTimePatterns(
            @PathVariable Integer channelId) {
        try {
            // 1. 사용자 인증
            Integer userId = authUtil.getCurrentUserId();
            if (userId == null) {
                log.warn("🚫 [인증 실패] 로그인이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            log.info("📡 [시간대별 악플 통계 조회 요청] channelId={}, userId={}", channelId, userId);

            // 2. 채널 소유권 검증
            validateChannelOwnership(channelId, userId);

            // 3. 서비스 호출
            Map<String, Object> response = dashboardTimePatternService.getTimePatterns(channelId);

            log.info("✅ [시간대별 악플 통계 조회 성공] channelId={}", channelId);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ [시간대별 악플 통계 조회 실패] channelId={}, error={}", channelId, e.getMessage());

            if (e.getMessage().contains("권한이 없습니다")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            } else if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            throw e;
        }
    }
}
