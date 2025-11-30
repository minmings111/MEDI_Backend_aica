package com.medi.backend.report.controller;

import com.medi.backend.filter.dto.FilterPreferenceRequest;
import com.medi.backend.filter.dto.FilterPreferenceResponse;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.report.dto.ReportRequest;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.redis.service.RedisQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보고서 생성 컨트롤러
 * 프론트에서 보고서 생성 요청 시 Redis 큐에 작업을 추가
 * 
 * ============================================
 * 보고서 API 엔드포인트 정리
 * ============================================
 * 
 * 1. 보고서 생성 요청 (통합)
 * POST /api/reports
 * 인증: 필요 (로그인)
 * Request Body:
 * {
 * "channelId": "UCxxxxx", // YouTube 채널 ID (필수)
 * "data": {} // 추가 데이터 (선택사항)
 * }
 * Response:
 * {
 * "message": "Report task added to queue successfully",
 * "channelId": "UCxxxxx",
 * "userId": 123
 * }
 * 동작: Redis DB 1의 report_agent:tasks:queue에 작업 추가 (type: "report")
 * 
 * 2. 입력폼 양식 생성 요청
 * POST /api/reports/form
 * 인증: 필요 (로그인)
 * Request Body:
 * {
 * "channelId": "UCxxxxx", // YouTube 채널 ID (필수)
 * "data": {
 * "selectedCategories": ["profanity", "appearance"],
 * "customRuleKeywords": {
 * "profanity": ["ㅅㅂ", "병X"]
 * },
 * "dislikeExamples": ["야 이 미친 새끼가"],
 * "allowExamples": ["컨디션 안 좋아보이네"]
 * }
 * }
 * Response:
 * {
 * "message": "Form data saved to DB and Redis successfully",
 * "channelId": "UCxxxxx",
 * "channelDbId": 456,
 * "userId": 123,
 * "preference": { ... }
 * }
 * 동작:
 * - DB 저장: user_filter_preferences 테이블 (MySQL)
 * - Redis 저장: channel:{channelId}:form (Redis DB 0, TTL 없음 - 영구 저장)
 * - 큐 작업: form_agent:tasks:queue (Redis DB 1)
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "보고서 생성 API")
public class ReportController {

    private final RedisQueueService redisQueueService;
    private final AuthUtil authUtil;
    private final FilterPreferenceService filterPreferenceService;
    private final YoutubeChannelMapper youtubeChannelMapper;

    public ReportController(
            RedisQueueService redisQueueService,
            AuthUtil authUtil,
            FilterPreferenceService filterPreferenceService,
            YoutubeChannelMapper youtubeChannelMapper) {
        this.redisQueueService = redisQueueService;
        this.authUtil = authUtil;
        this.filterPreferenceService = filterPreferenceService;
        this.youtubeChannelMapper = youtubeChannelMapper;
    }

    /**
     * 보고서 생성 요청 (통합)
     * POST /api/reports
     * 
     * @param request 보고서 생성 요청 (channelId, 추가 데이터)
     * @return 큐 추가 성공 응답
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @Operation(summary = "보고서 생성 요청", description = "보고서 생성을 위한 작업을 큐에 추가합니다.")
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody ReportRequest request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getChannelId() == null || request.getChannelId().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "channelId is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // 프론트에서 전달받은 추가 데이터를 Map으로 변환
            Map<String, Object> requestData = request.getData() != null ? request.getData() : new HashMap<>();

            // 통합된 큐에 작업 추가 (type="report" 고정)
            redisQueueService.enqueueReport(request.getChannelId(), userId, "report", requestData);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Report task added to queue successfully");
            response.put("channelId", request.getChannelId());
            response.put("userId", userId);

            log.info("✅ 보고서 생성 요청: userId={}, channelId={}", userId, request.getChannelId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ 보고서 생성 요청 실패: userId={}, channelId={}", userId, request.getChannelId(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to add report task to queue");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 입력폼 양식 (Form) 생성 요청
     * POST /api/reports/form
     * - 필터링 설정 데이터를 user_filter_preferences 테이블에 저장
     * - 작업 큐에 추가하지 않고 별도로 처리
     * 
     * @param request 폼 생성 요청 (channelId: YouTube channel ID, data: 필터링 설정 데이터)
     * @return 폼 데이터 저장 성공 응답
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/form")
    @Operation(summary = "입력폼 양식 생성 요청", description = "필터링 설정 데이터를 user_filter_preferences 테이블에 저장합니다.")
    public ResponseEntity<Map<String, Object>> createForm(@RequestBody ReportRequest request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getChannelId() == null || request.getChannelId().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "channelId is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            // 프론트에서 전달받은 필터링 설정 데이터
            Map<String, Object> requestData = request.getData() != null ? request.getData() : new HashMap<>();

            // ✅ YouTube channel ID로 DB channel ID 조회
            YoutubeChannelDto channel = youtubeChannelMapper.findByYoutubeChannelId(request.getChannelId());
            if (channel == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "Channel not found: " + request.getChannelId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            // ✅ 사용자 소유 채널인지 확인
            if (!channel.getUserId().equals(userId)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "Unauthorized: Channel does not belong to user");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }

            Integer channelDbId = channel.getId();

            // ✅ ReportRequest.data를 FilterPreferenceRequest로 변환
            FilterPreferenceRequest filterRequest = convertToFilterPreferenceRequest(requestData, channelDbId);

            // ✅ 1. user_filter_preferences 테이블에 저장 (DB)
            // savePreference 내부에서 Redis(DB 0)에 channel:{channelId}:form 도 함께 저장됨
            FilterPreferenceResponse savedPreference = filterPreferenceService.savePreference(userId, filterRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Form data saved to DB and Redis successfully");
            response.put("channelId", request.getChannelId());
            response.put("channelDbId", channelDbId);
            response.put("userId", userId);
            response.put("preference", savedPreference);

            log.info("✅ 입력폼 양식 데이터 저장 완료 (DB): userId={}, channelId={}, channelDbId={}",
                    userId, request.getChannelId(), channelDbId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ 입력폼 양식 데이터 저장 실패: userId={}, channelId={}", userId, request.getChannelId(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to save form data");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * ReportRequest.data를 FilterPreferenceRequest로 변환
     */
    @SuppressWarnings("unchecked")
    private FilterPreferenceRequest convertToFilterPreferenceRequest(Map<String, Object> data, Integer channelDbId) {
        FilterPreferenceRequest request = new FilterPreferenceRequest();
        request.setChannelId(channelDbId);

        // Step 1: 카테고리 선택
        if (data.containsKey("selectedCategories")) {
            Object categories = data.get("selectedCategories");
            if (categories instanceof List) {
                request.setSelectedCategories((List<String>) categories);
            }
        }

        // Step 2: 사용자 필터링 설명
        if (data.containsKey("userFilteringDescription")) {
            Object description = data.get("userFilteringDescription");
            if (description instanceof String) {
                request.setUserFilteringDescription((String) description);
            }
        }

        // Step 3: 예시 라벨링
        if (data.containsKey("dislikeExamples")) {
            Object dislike = data.get("dislikeExamples");
            if (dislike instanceof List) {
                request.setDislikeExamples((List<String>) dislike);
            }
        }

        if (data.containsKey("allowExamples")) {
            Object allow = data.get("allowExamples");
            if (allow instanceof List) {
                request.setAllowExamples((List<String>) allow);
            }
        }

        return request;
    }
}
