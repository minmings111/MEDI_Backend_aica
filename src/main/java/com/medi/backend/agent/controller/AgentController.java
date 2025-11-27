package com.medi.backend.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.dto.AgentProfilingRequest;
import com.medi.backend.agent.dto.FilteredCommentResponse;
import com.medi.backend.agent.dto.AnalysisSummaryResponse;
import com.medi.backend.agent.dto.FilteredCommentStatsResponse;
import com.medi.backend.agent.dto.DailyCommentStatDto;
import com.medi.backend.agent.service.AgentService;
import com.medi.backend.global.util.AuthUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/analysis")
public class AgentController {

    private final AgentService agentService;
    private final AuthUtil authUtil;

    public AgentController(AgentService agentService, AuthUtil authUtil) {
        this.agentService = agentService;
        this.authUtil = authUtil;
    }
    
    /**
     * AI 서버에서 필터링된 댓글 결과를 받는 엔드포인트
     * 
     * @param request AI 분석 결과 (video_id, comments 배열 포함)
     * @return 저장 성공 응답
     */
    @PostMapping("filtered-results")
    public ResponseEntity<Map<String, Object>> receiveFilteredComments(
        @RequestBody AgentFilteredCommentsRequest request
    ) {
        Integer savedCount = agentService.insertFilteredComment(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Filtered comments saved successfully");
        response.put("savedCount", savedCount);
        
        int totalReceived = 0;
        if (request.getFilteredComments() != null) totalReceived += request.getFilteredComments().size();
        if (request.getContentSuggestions() != null) totalReceived += request.getContentSuggestions().size();
        
        response.put("totalReceived", totalReceived);
        response.put("videoId", request.getVideoId());
        response.put("channelId", request.getChannelId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * AI 서버에서 프로파일링 결과를 받는 엔드포인트
     * 
     * @param request AI 프로파일링 결과 (channelId, profileData, metadata 포함)
     * @return 저장 성공 응답
     */
    @PostMapping("/profile-results")
    public ResponseEntity<Map<String, Object>> receiveProfilingResults(
        @RequestBody AgentProfilingRequest request
    ) {
        Integer saved = agentService.insertChannelProfiling(request);
        
        Map<String, Object> response = new HashMap<>();
        if (saved > 0) {
            response.put("message", "Channel profiling saved successfully");
            response.put("success", true);
        } else {
            response.put("message", "Failed to save channel profiling");
            response.put("success", false);
        }
        response.put("channelId", request.getChannelId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 비디오별 필터링된 댓글 조회
     * 
     * @param videoId 내부 비디오 ID
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - 선택사항, 없으면 전체
     * @return 필터링된 댓글 목록 + 분석 요약
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/video/{videoId}")
    public ResponseEntity<Map<String, Object>> getFilteredCommentsByVideoId(
        @PathVariable("videoId") Integer videoId,
        @RequestParam(value = "status", required = false) String status
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [API 요청] 비디오별 필터링된 댓글 조회: videoId={}, userId={}, status={}", videoId, userId, status);
        
        List<FilteredCommentResponse> comments = agentService.getFilteredCommentsByVideoId(videoId, userId, status);
        AnalysisSummaryResponse summary = agentService.getAnalysisSummaryByVideoId(videoId, userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("comments", comments);
        response.put("summary", summary);
        response.put("totalCount", comments != null ? comments.size() : 0);
        response.put("maxLimit", 200);  // 최대 조회 개수 제한
        
        log.info("📡 [API 응답] 비디오별 필터링된 댓글 조회 완료: videoId={}, 댓글수={}개", videoId, comments != null ? comments.size() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 채널별 필터링된 댓글 조회
     * 
     * @param channelId 내부 채널 ID
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - 선택사항, 없으면 전체
     * @return 필터링된 댓글 목록
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/channel/{channelId}")
    public ResponseEntity<Map<String, Object>> getFilteredCommentsByChannelId(
        @PathVariable("channelId") Integer channelId,
        @RequestParam(value = "status", required = false) String status
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [API 요청] 채널별 필터링된 댓글 조회: channelId={}, userId={}, status={}", channelId, userId, status);
        
        List<FilteredCommentResponse> comments = agentService.getFilteredCommentsByChannelId(channelId, userId, status);
        
        Map<String, Object> response = new HashMap<>();
        response.put("comments", comments);
        response.put("totalCount", comments != null ? comments.size() : 0);
        response.put("maxLimit", 500);  // 최대 조회 개수 제한
        
        log.info("📡 [API 응답] 채널별 필터링된 댓글 조회 완료: channelId={}, 댓글수={}개", channelId, comments != null ? comments.size() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 사용자별 필터링된 댓글 조회 (내 모든 채널)
     * 
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - 선택사항, 없으면 전체
     * @return 필터링된 댓글 목록
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/my")
    public ResponseEntity<Map<String, Object>> getFilteredCommentsByUserId(
        @RequestParam(value = "status", required = false) String status
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [API 요청] 사용자별 필터링된 댓글 조회: userId={}, status={}", userId, status);
        
        List<FilteredCommentResponse> comments = agentService.getFilteredCommentsByUserId(userId, status);
        
        Map<String, Object> response = new HashMap<>();
        response.put("comments", comments);
        response.put("totalCount", comments != null ? comments.size() : 0);
        response.put("maxLimit", 1000);  // 최대 조회 개수 제한
        
        log.info("📡 [API 응답] 사용자별 필터링된 댓글 조회 완료: userId={}, 댓글수={}개", userId, comments != null ? comments.size() : 0);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 날짜별 필터링된 댓글 통계 조회
     * 
     * @param videoId 비디오 ID (선택사항)
     * @param channelId 채널 ID (선택사항)
     * @param periodType 날짜 단위 ("daily", "monthly", "yearly") - 기본값: "daily"
     * @param startDate 시작 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @param endDate 종료 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @return 날짜별 통계
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/stats")
    public ResponseEntity<FilteredCommentStatsResponse> getFilteredCommentStats(
        @RequestParam(value = "videoId", required = false) Integer videoId,
        @RequestParam(value = "channelId", required = false) Integer channelId,
        @RequestParam(value = "period", defaultValue = "daily") String periodType,
        @RequestParam(value = "startDate", required = false) String startDate,
        @RequestParam(value = "endDate", required = false) String endDate
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [API 요청] 날짜별 필터링된 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        FilteredCommentStatsResponse stats = agentService.getFilteredCommentStatsByDate(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        log.info("📡 [API 응답] 날짜별 필터링된 댓글 통계 조회 완료: userId={}, 통계 항목수={}개", 
            userId, stats != null && stats.getStats() != null ? stats.getStats().size() : 0);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 일별 전체 댓글 통계 조회 (daily_comment_stats 테이블)
     * - 전체 댓글 수 (total_count)와 필터링된 댓글 수 (filtered_count) 포함
     * - 그래프용: "전체 댓글 수 vs 필터링된 댓글 수" 비교 가능
     * 
     * API 사용법:
     * GET /api/v1/analysis/comments/daily-stats
     * 인증: 필요 (로그인)
     * 
     * Query Parameters (모두 선택사항):
     *   - videoId: 비디오 ID (Integer)
     *   - channelId: 채널 ID (Integer)
     *   - period: 날짜 단위 ("daily", "monthly", "yearly") - 기본값: "daily"
     *   - startDate: 시작 날짜 (형식: "YYYY-MM-DD", 예: "2024-01-01")
     *   - endDate: 종료 날짜 (형식: "YYYY-MM-DD", 예: "2024-01-31")
     * 
     * 예시 요청:
     *   GET /api/v1/analysis/comments/daily-stats?channelId=1&startDate=2024-01-01&endDate=2024-01-31
     *   GET /api/v1/analysis/comments/daily-stats?videoId=5&period=daily
     *   GET /api/v1/analysis/comments/daily-stats?period=monthly
     * 
     * Response:
     *   [
     *     {
     *       "statDate": "2024-01-01",
     *       "totalCount": 100,        // AI가 분석한 전체 댓글 수
     *       "filteredCount": 20,      // 필터링된 댓글 수
     *       "youtubeTotalCount": 150  // YouTube Data API에서 가져온 실제 전체 댓글 수 (null 가능)
     *     },
     *     ...
     *   ]
     * 
     * @param videoId 비디오 ID (선택사항)
     * @param channelId 채널 ID (선택사항)
     * @param periodType 날짜 단위 ("daily", "monthly", "yearly") - 기본값: "daily"
     * @param startDate 시작 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @param endDate 종료 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @return 날짜별 통계 (전체 댓글 수 포함)
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comments/daily-stats")
    public ResponseEntity<List<DailyCommentStatDto>> getDailyCommentStats(
        @RequestParam(value = "videoId", required = false) Integer videoId,
        @RequestParam(value = "channelId", required = false) Integer channelId,
        @RequestParam(value = "period", defaultValue = "daily") String periodType,
        @RequestParam(value = "startDate", required = false) String startDate,
        @RequestParam(value = "endDate", required = false) String endDate
    ) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("📡 [API 요청] 일별 전체 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        List<DailyCommentStatDto> stats = agentService.getDailyCommentStats(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        log.info("📡 [API 응답] 일별 전체 댓글 통계 조회 완료: userId={}, 통계 항목수={}개", 
            userId, stats != null ? stats.size() : 0);
        
        return ResponseEntity.ok(stats);
    }
}

