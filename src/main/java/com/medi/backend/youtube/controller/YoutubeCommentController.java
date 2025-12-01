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
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{youtubeCommentId}")
    @Operation(summary = "댓글 삭제", description = "YouTube 댓글을 삭제합니다. (할당량: 50 units)")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable String youtubeCommentId) {
        Integer userId = authUtil.getCurrentUserId();
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
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/batch")
    @Operation(summary = "일괄 댓글 삭제", description = "여러 댓글을 한 번에 삭제합니다. (할당량: 댓글당 50 units)")
    public ResponseEntity<Map<String, Object>> deleteCommentsBatch(
            @RequestBody Map<String, List<String>> request) {
        Integer userId = authUtil.getCurrentUserId();
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
}
