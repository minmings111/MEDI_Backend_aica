package com.medi.backend.youtube.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.medi.backend.agent.mapper.AgentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YouTube 댓글 삭제 서비스
 */
@Slf4j
@Service
public class YoutubeCommentDeletionService {

    private final YoutubeOAuthService youtubeOAuthService;
    private final AgentMapper agentMapper;

    public YoutubeCommentDeletionService(
            YoutubeOAuthService youtubeOAuthService,
            AgentMapper agentMapper) {
        this.youtubeOAuthService = youtubeOAuthService;
        this.agentMapper = agentMapper;
    }

    /**
     * 단일 댓글 삭제
     */
    @Transactional
    public void deleteComment(Integer userId, String youtubeCommentId) {
        // 1. 권한 검증
        Integer ownershipCount = agentMapper.checkCommentOwnership(userId, youtubeCommentId);
        if (ownershipCount == 0) {
            throw new IllegalArgumentException("이 댓글을 삭제할 권한이 없습니다.");
        }

        // 2. OAuth 토큰 가져오기
        String accessToken = youtubeOAuthService.getValidAccessToken(userId);

        // 3. YouTube API 호출 및 결과 추적
        String youtubeDeletionStatus = deleteCommentFromYoutubeInternal(accessToken, youtubeCommentId);

        // 4. DB 업데이트 (Soft Delete)
        agentMapper.updateCommentStatusToDeleted(youtubeCommentId, youtubeDeletionStatus);

        // 5. 할당량 로그 출력
        log.info("✅ [댓글 삭제 완료] userId={}, youtubeCommentId={}, status={}, YouTube API 할당량 사용: 50 units",
                userId, youtubeCommentId, youtubeDeletionStatus);
    }

    /**
     * 일괄 댓글 삭제 (댓글 ID 리스트 기반)
     */
    @Transactional
    public Map<String, Object> deleteCommentsBatch(Integer userId, List<String> youtubeCommentIds) {
        List<String> successIds = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();

        for (String commentId : youtubeCommentIds) {
            try {
                deleteComment(userId, commentId);
                successIds.add(commentId);
            } catch (Exception e) {
                log.error("❌ [댓글 삭제 실패] commentId={}, error={}", commentId, e.getMessage());
                failures.add(Map.of(
                        "youtubeCommentId", commentId,
                        "error", e.getMessage()));
            }
        }

        // 할당량 정보 계산
        int quotaUsed = successIds.size() * 50;

        Map<String, Object> result = new HashMap<>();
        result.put("totalRequested", youtubeCommentIds.size());
        result.put("successCount", successIds.size());
        result.put("failureCount", failures.size());
        result.put("quotaUsed", quotaUsed);
        result.put("successIds", successIds);
        result.put("failures", failures);

        // 할당량 로그 출력
        log.info("📊 [일괄 댓글 삭제 완료] userId={}, 성공={}, 실패={}, YouTube API 할당량 사용: {} units",
                userId, successIds.size(), failures.size(), quotaUsed);

        return result;
    }

    /**
     * 특정 비디오의 필터링된 댓글 전체 삭제
     */
    @Transactional
    public Map<String, Object> deleteFilteredCommentsByVideoId(Integer userId, Integer videoId) {
        // 1. 필터링된 댓글 조회 (status='filtered'만)
        List<com.medi.backend.agent.dto.FilteredCommentResponse> filteredComments = agentMapper
                .findFilteredCommentsByVideoId(videoId, userId, "filtered");

        if (filteredComments.isEmpty()) {
            log.info("ℹ️ [삭제할 댓글 없음] userId={}, videoId={}", userId, videoId);
            return Map.of(
                    "totalRequested", 0,
                    "successCount", 0,
                    "failureCount", 0,
                    "quotaUsed", 0,
                    "message", "삭제할 필터링된 댓글이 없습니다.");
        }

        // 2. 댓글 ID 리스트 추출
        List<String> commentIds = filteredComments.stream()
                .map(com.medi.backend.agent.dto.FilteredCommentResponse::getYoutubeCommentId)
                .toList();

        log.info("🗑️ [비디오 필터링 댓글 전체 삭제 시작] userId={}, videoId={}, count={}",
                userId, videoId, commentIds.size());

        // 3. 배치 삭제 실행
        return deleteCommentsBatch(userId, commentIds);
    }

    /**
     * 특정 채널의 필터링된 댓글 전체 삭제
     */
    @Transactional
    public Map<String, Object> deleteFilteredCommentsByChannelId(Integer userId, Integer channelId) {
        // 1. 필터링된 댓글 조회 (status='filtered'만)
        List<com.medi.backend.agent.dto.FilteredCommentResponse> filteredComments = agentMapper
                .findFilteredCommentsByChannelId(channelId, userId, "filtered");

        if (filteredComments.isEmpty()) {
            log.info("ℹ️ [삭제할 댓글 없음] userId={}, channelId={}", userId, channelId);
            return Map.of(
                    "totalRequested", 0,
                    "successCount", 0,
                    "failureCount", 0,
                    "quotaUsed", 0,
                    "message", "삭제할 필터링된 댓글이 없습니다.");
        }

        // 2. 댓글 ID 리스트 추출
        List<String> commentIds = filteredComments.stream()
                .map(com.medi.backend.agent.dto.FilteredCommentResponse::getYoutubeCommentId)
                .toList();

        log.info("🗑️ [채널 필터링 댓글 전체 삭제 시작] userId={}, channelId={}, count={}",
                userId, channelId, commentIds.size());

        // 3. 배치 삭제 실행
        return deleteCommentsBatch(userId, commentIds);
    }

    /**
     * 비동기 비디오 필터링 댓글 전체 삭제 (즉시 응답)
     */
    @Transactional
    public Map<String, Object> requestAsyncDeletionByVideoId(Integer userId, Integer videoId) {
        String requestId = java.util.UUID.randomUUID().toString();

        // 1. ACTIVE 댓글을 PENDING_DELETE로 변경
        int markedCount = agentMapper.markCommentsForDeletion(videoId, requestId);

        if (markedCount == 0) {
            log.info("ℹ️ [비동기 삭제 요청 - 댓글 없음] userId={}, videoId={}", userId, videoId);
            return Map.of(
                    "requestId", requestId,
                    "totalComments", 0,
                    "message", "삭제할 필터링된 댓글이 없습니다.");
        }

        log.info("📝 [비동기 삭제 요청] userId={}, videoId={}, requestId={}, count={}",
                userId, videoId, requestId, markedCount);

        return Map.of(
                "requestId", requestId,
                "totalComments", markedCount,
                "message", "삭제 작업이 시작되었습니다. 진행 상황은 requestId로 조회하세요.");
    }

    /**
     * 비동기 채널 필터링 댓글 전체 삭제 (즉시 응답)
     */
    @Transactional
    public Map<String, Object> requestAsyncDeletionByChannelId(Integer userId, Integer channelId) {
        String requestId = java.util.UUID.randomUUID().toString();

        // 1. ACTIVE 댓글을 PENDING_DELETE로 변경
        int markedCount = agentMapper.markChannelCommentsForDeletion(channelId, userId, requestId);

        if (markedCount == 0) {
            log.info("ℹ️ [비동기 삭제 요청 - 댓글 없음] userId={}, channelId={}", userId, channelId);
            return Map.of(
                    "requestId", requestId,
                    "totalComments", 0,
                    "message", "삭제할 필터링된 댓글이 없습니다.");
        }

        log.info("📝 [비동기 삭제 요청] userId={}, channelId={}, requestId={}, count={}",
                userId, channelId, requestId, markedCount);

        return Map.of(
                "requestId", requestId,
                "totalComments", markedCount,
                "message", "삭제 작업이 시작되었습니다. 진행 상황은 requestId로 조회하세요.");
    }

    /**
     * 삭제 작업 진행 상황 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getJobProgress(String requestId) {
        Map<String, Object> progress = agentMapper.getDeletionJobProgress(requestId);

        if (progress == null || ((Number) progress.get("totalComments")).intValue() == 0) {
            throw new IllegalArgumentException("존재하지 않는 요청 ID입니다.");
        }

        // 진행률 계산
        int total = ((Number) progress.get("totalComments")).intValue();
        int completed = ((Number) progress.get("completedComments")).intValue();
        int failed = ((Number) progress.get("failedComments")).intValue();
        double progressPercentage = ((double) (completed + failed) / total) * 100;
        boolean isCompleted = (completed + failed) == total;

        progress.put("progressPercentage", Math.round(progressPercentage * 100.0) / 100.0);
        progress.put("isCompleted", isCompleted);

        return progress;
    }

    /**
     * YouTube API 댓글 삭제 (내부용 - Background Worker에서도 사용)
     * 
     * @return YouTube 삭제 상태 (SUCCESS, NOT_FOUND)
     */
    public String deleteCommentFromYoutubeInternal(String accessToken, String youtubeCommentId) {
        try {
            YouTube youtube = new YouTube.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("Medi-Backend").build();

            youtube.comments().delete(youtubeCommentId).execute();

            log.info("✅ [YouTube API 호출 성공] 댓글 삭제: commentId={}", youtubeCommentId);
            return "SUCCESS";

        } catch (GoogleJsonResponseException e) {
            return handleYoutubeApiError(e, youtubeCommentId);
        } catch (Exception e) {
            throw new RuntimeException("YouTube API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * YouTube API 오류 처리
     * 
     * @return YouTube 삭제 상태 (NOT_FOUND만 반환, 나머지는 예외 발생)
     */
    private String handleYoutubeApiError(GoogleJsonResponseException e, String youtubeCommentId) {
        int statusCode = e.getStatusCode();

        switch (statusCode) {
            case 404:
                log.warn("⚠️ [댓글 없음] 댓글을 찾을 수 없음 (이미 삭제됨): commentId={}", youtubeCommentId);
                return "NOT_FOUND"; // DB에 NOT_FOUND 상태로 기록

            case 403:
                log.error("❌ [권한 없음] 댓글 삭제 권한이 없습니다: commentId={}", youtubeCommentId);
                throw new IllegalArgumentException("댓글 삭제 권한이 없습니다.");

            case 429:
                log.error("❌ [할당량 초과] YouTube API 할당량을 초과했습니다 (일일 한도: 10,000 units)");
                throw new RuntimeException(
                        "YouTube API 할당량을 초과했습니다. 내일 다시 시도해주세요. (일일 한도: 10,000 units)");

            default:
                log.error("❌ [YouTube API 오류] statusCode={}, message={}", statusCode, e.getMessage());
                throw new RuntimeException("YouTube API 호출 실패: " + e.getMessage(), e);
        }
    }
}
