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

        // 3. YouTube API 호출
        deleteCommentFromYoutube(accessToken, youtubeCommentId);

        // 4. DB 업데이트 (소프트 삭제)
        agentMapper.updateCommentStatusToDeleted(youtubeCommentId);

        // 5. 할당량 로그 출력
        log.info("✅ [댓글 삭제 완료] userId={}, youtubeCommentId={}, YouTube API 할당량 사용: 50 units",
                userId, youtubeCommentId);
    }

    /**
     * 일괄 댓글 삭제
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
     * YouTube API 댓글 삭제
     */
    private void deleteCommentFromYoutube(String accessToken, String youtubeCommentId) {
        try {
            YouTube youtube = new YouTube.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("Medi-Backend").build();

            youtube.comments().delete(youtubeCommentId).execute();

            log.info("✅ [YouTube API 호출 성공] 댓글 삭제: commentId={}", youtubeCommentId);

        } catch (GoogleJsonResponseException e) {
            handleYoutubeApiError(e, youtubeCommentId);
        } catch (Exception e) {
            throw new RuntimeException("YouTube API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * YouTube API 오류 처리
     */
    private void handleYoutubeApiError(GoogleJsonResponseException e, String youtubeCommentId) {
        int statusCode = e.getStatusCode();

        switch (statusCode) {
            case 404:
                log.warn("⚠️ [댓글 없음] 댓글을 찾을 수 없음 (이미 삭제됨): commentId={}", youtubeCommentId);
                break;

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
