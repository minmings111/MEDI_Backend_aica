package com.medi.backend.youtube.scheduler;

import com.medi.backend.agent.mapper.AgentMapper;
import com.medi.backend.youtube.service.YoutubeCommentDeletionService;
import com.medi.backend.youtube.service.YoutubeOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 비동기 댓글 삭제 Background Worker
 * 10초마다 PENDING_DELETE 상태의 댓글을 처리합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommentDeletionWorker {

    private final AgentMapper agentMapper;
    private final YoutubeCommentDeletionService deletionService;
    private final YoutubeOAuthService youtubeOAuthService;

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 10; // 한 번에 10개씩 처리

    /**
     * 10초마다 PENDING_DELETE 댓글 처리
     */
    @Scheduled(fixedDelay = 10000) // 10초
    public void processPendingDeletions() {
        try {
            List<Map<String, Object>> pendingComments = agentMapper.findPendingDeletionComments(
                    MAX_RETRY_COUNT,
                    BATCH_SIZE);

            if (pendingComments.isEmpty()) {
                return; // 처리할 댓글이 없으면 종료
            }

            log.info("🔄 [Background Worker] 처리할 댓글 수: {}", pendingComments.size());

            for (Map<String, Object> comment : pendingComments) {
                processComment(comment);
            }

        } catch (Exception e) {
            log.error("❌ [Background Worker 오류] {}", e.getMessage(), e);
        }
    }

    /**
     * 개별 댓글 처리
     */
    private void processComment(Map<String, Object> comment) {
        String youtubeCommentId = (String) comment.get("youtubeCommentId");
        Integer userId = (Integer) comment.get("userId");
        Integer retryCount = (Integer) comment.get("deletionRetryCount");

        try {
            // 1. OAuth 토큰 가져오기
            String accessToken = youtubeOAuthService.getValidAccessToken(userId);

            // 2. YouTube API 호출
            String youtubeDeletionStatus = deletionService.deleteCommentFromYoutubeInternal(
                    accessToken,
                    youtubeCommentId);

            // 3. DB 업데이트 (DELETED 상태로 변경)
            agentMapper.updateCommentStatusToDeleted(
                    youtubeCommentId,
                    youtubeDeletionStatus);

            log.info("✅ [댓글 삭제 성공] commentId={}, status={}", youtubeCommentId, youtubeDeletionStatus);

        } catch (Exception e) {
            handleDeletionError(youtubeCommentId, retryCount, e);
        }
    }

    /**
     * 삭제 오류 처리
     */
    private void handleDeletionError(String youtubeCommentId, Integer retryCount, Exception e) {
        String errorMessage = e.getMessage();

        // Quota 초과 시 1시간 후 재시도
        if (errorMessage != null && errorMessage.contains("quota")) {
            agentMapper.incrementDeletionRetryCount(
                    youtubeCommentId,
                    errorMessage,
                    Instant.now().plus(1, ChronoUnit.HOURS));
            log.warn("⚠️ [Quota 초과] 1시간 후 재시도: {}", youtubeCommentId);
            return;
        }

        // 일반 오류 시 즉시 재시도
        agentMapper.incrementDeletionRetryCount(
                youtubeCommentId,
                errorMessage,
                Instant.now());

        if (retryCount + 1 >= MAX_RETRY_COUNT) {
            log.error("❌ [최대 재시도 초과] commentId={}, retryCount={}", youtubeCommentId, retryCount + 1);
        } else {
            log.warn("⚠️ [삭제 실패, 재시도 예정] commentId={}, retryCount={}, error={}",
                    youtubeCommentId, retryCount + 1, errorMessage);
        }
    }
}
