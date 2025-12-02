package com.medi.backend.youtube.controller;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.youtube.service.YoutubeCommentDeletionService;
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
 * YouTube 댓글 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/youtube/comments")
@Tag(name = "YouTube Comments", description = "YouTube 댓글 관리 API")
public class YoutubeCommentController {

    private final YoutubeCommentDeletionService commentDeletionService;
    private final AuthUtil authUtil;

    public YoutubeCommentController(
            YoutubeCommentDeletionService commentDeletionService,
            AuthUtil authUtil) {
        this.commentDeletionService = commentDeletionService;
        this.authUtil = authUtil;
    }

    /**
     * 단일 댓글 삭제
     * DELETE /api/youtube/comments/{youtubeCommentId}
     * 
     * @param youtubeCommentId YouTube 댓글 ID (예: UgxABC123...)
     * @return 삭제 성공 응답
     */
    @DeleteMapping("/{youtubeCommentId}")
    @Operation(summary = "댓글 삭제", description = "YouTube 댓글을 삭제합니다. (할당량: 50 units)")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable String youtubeCommentId,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            log.info("🗑️ [댓글 삭제 요청] userId={}, youtubeCommentId={}", userId, youtubeCommentId);

            commentDeletionService.deleteComment(userId, youtubeCommentId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Comment deleted successfully");
            response.put("youtubeCommentId", youtubeCommentId);
            response.put("quotaUsed", 50);

            log.info("✅ [댓글 삭제 성공] userId={}, youtubeCommentId={}", userId, youtubeCommentId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ [권한 오류] userId={}, youtubeCommentId={}, error={}",
                    userId, youtubeCommentId, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);

        } catch (Exception e) {
            log.error("❌ [댓글 삭제 실패] userId={}, youtubeCommentId={}, error={}",
                    userId, youtubeCommentId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to delete comment");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 일괄 댓글 삭제
     * DELETE /api/youtube/comments/batch
     * 
     * @param request 삭제할 댓글 ID 리스트 { "youtubeCommentIds": ["UgxABC...",
     *                "UgxDEF..."] }
     * @return 삭제 결과 (성공/실패 개수)
     */
    @DeleteMapping("/batch")
    @Operation(summary = "일괄 댓글 삭제", description = "여러 댓글을 한 번에 삭제합니다. (할당량: 댓글당 50 units)")
    public ResponseEntity<Map<String, Object>> deleteCommentsBatch(
            @RequestBody Map<String, List<String>> request,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> youtubeCommentIds = request.get("youtubeCommentIds");

        if (youtubeCommentIds == null || youtubeCommentIds.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "youtubeCommentIds is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            log.info("🗑️ [일괄 댓글 삭제 요청] userId={}, count={}", userId, youtubeCommentIds.size());

            Map<String, Object> result = commentDeletionService.deleteCommentsBatch(userId, youtubeCommentIds);

            log.info("✅ [일괄 댓글 삭제 완료] userId={}, 성공={}, 실패={}",
                    userId, result.get("successCount"), result.get("failureCount"));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [일괄 댓글 삭제 실패] userId={}, error={}", userId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to delete comments");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 특정 비디오의 필터링된 댓글 전체 삭제
     * DELETE /api/youtube/comments/video/{videoId}/filtered
     * 
     * @param videoId 비디오 ID (내부 ID)
     * @return 삭제 결과 (성공/실패 개수)
     */
    @DeleteMapping("/video/{videoId}/filtered")
    @Operation(summary = "비디오 필터링 댓글 전체 삭제", description = "특정 비디오의 필터링된 댓글을 모두 삭제합니다. (할당량: 댓글당 50 units)")
    public ResponseEntity<Map<String, Object>> deleteFilteredCommentsByVideo(
            @PathVariable Integer videoId,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            log.info("🗑️ [비디오 필터링 댓글 전체 삭제 요청] userId={}, videoId={}", userId, videoId);

            Map<String, Object> result = commentDeletionService.deleteFilteredCommentsByVideoId(userId, videoId);

            log.info("✅ [비디오 필터링 댓글 전체 삭제 완료] userId={}, videoId={}, 성공={}, 실패={}",
                    userId, videoId, result.get("successCount"), result.get("failureCount"));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [비디오 필터링 댓글 전체 삭제 실패] userId={}, videoId={}, error={}",
                    userId, videoId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to delete filtered comments");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 특정 채널의 필터링된 댓글 전체 삭제
     * DELETE /api/youtube/comments/channel/{channelId}/filtered
     * 
     * @param channelId 채널 ID (내부 ID)
     * @return 삭제 결과 (성공/실패 개수)
     */
    @DeleteMapping("/channel/{channelId}/filtered")
    @Operation(summary = "채널 필터링 댓글 전체 삭제", description = "특정 채널의 필터링된 댓글을 모두 삭제합니다. (할당량: 댓글당 50 units)")
    public ResponseEntity<Map<String, Object>> deleteFilteredCommentsByChannel(
            @PathVariable Integer channelId,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            log.info("🗑️ [채널 필터링 댓글 전체 삭제 요청] userId={}, channelId={}", userId, channelId);

            Map<String, Object> result = commentDeletionService.deleteFilteredCommentsByChannelId(userId, channelId);

            log.info("✅ [채널 필터링 댓글 전체 삭제 완료] userId={}, channelId={}, 성공={}, 실패={}",
                    userId, channelId, result.get("successCount"), result.get("failureCount"));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [채널 필터링 댓글 전체 삭제 실패] userId={}, channelId={}, error={}",
                    userId, channelId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to delete filtered comments");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 비동기 비디오 필터링 댓글 전체 삭제
     * DELETE /api/youtube/comments/video/{videoId}/filtered/async
     * 
     * @param videoId 비디오 ID (내부 ID)
     * @return 삭제 요청 ID 및 총 댓글 수
     */
    @DeleteMapping("/video/{videoId}/filtered/async")
    @Operation(summary = "비동기 비디오 필터링 댓글 전체 삭제", description = "특정 비디오의 필터링된 댓글을 비동기로 삭제합니다. 즉시 requestId를 반환하며, 진행 상황은 별도 API로 조회합니다.")
    public ResponseEntity<Map<String, Object>> requestAsyncDeletionByVideo(
            @PathVariable Integer videoId,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<String, Object> result = commentDeletionService.requestAsyncDeletionByVideoId(userId, videoId);
            return ResponseEntity.accepted().body(result);

        } catch (Exception e) {
            log.error("❌ [비동기 삭제 요청 실패] userId={}, videoId={}, error={}",
                    userId, videoId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to request async deletion");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 비동기 채널 필터링 댓글 전체 삭제
     * DELETE /api/youtube/comments/channel/{channelId}/filtered/async
     * 
     * @param channelId 채널 ID (내부 ID)
     * @return 삭제 요청 ID 및 총 댓글 수
     */
    @DeleteMapping("/channel/{channelId}/filtered/async")
    @Operation(summary = "비동기 채널 필터링 댓글 전체 삭제", description = "특정 채널의 필터링된 댓글을 비동기로 삭제합니다. 즉시 requestId를 반환하며, 진행 상황은 별도 API로 조회합니다.")
    public ResponseEntity<Map<String, Object>> requestAsyncDeletionByChannel(
            @PathVariable Integer channelId,
            @RequestParam(required = false) Integer requestUserId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            userId = requestUserId;
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<String, Object> result = commentDeletionService.requestAsyncDeletionByChannelId(userId, channelId);
            return ResponseEntity.accepted().body(result);

        } catch (Exception e) {
            log.error("❌ [비동기 삭제 요청 실패] userId={}, channelId={}, error={}",
                    userId, channelId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to request async deletion");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 삭제 작업 진행 상황 조회
     * GET /api/youtube/comments/deletion-status/{requestId}
     * 
     * @param requestId 삭제 요청 ID
     * @return 진행 상황 (총 댓글 수, 완료 수, 실패 수, 진행률)
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/deletion-status/{requestId}")
    @Operation(summary = "삭제 작업 진행 상황 조회", description = "비동기 삭제 작업의 진행 상황을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getJobProgress(
            @PathVariable String requestId) {
        try {
            Map<String, Object> progress = commentDeletionService.getJobProgress(requestId);
            return ResponseEntity.ok(progress);

        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);

        } catch (Exception e) {
            log.error("❌ [진행 상황 조회 실패] requestId={}, error={}", requestId, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Failed to get job progress");
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
