package com.medi.backend.youtube.redis.mapper;

import org.springframework.stereotype.Component;

import com.google.api.services.youtube.model.Comment;
import com.medi.backend.youtube.redis.dto.RedisYoutubeComment;
import com.medi.backend.youtube.redis.dto.RedisYoutubeCommentFull;

/**
 * YouTube API Comment 객체를 Redis 저장용 YoutubeComment DTO로 변환하는 매퍼
 * 
 * 주요 변환 작업:
 * 1. YouTube API의 Comment 객체에서 필요한 필드 추출
 * 2. AI 서버 호환을 위해 스네이크 케이스 필드명으로 매핑
 * 3. 날짜/시간을 ISO 8601 형식 문자열로 변환
 * 
 * 참고:
 * - channel_comment_fetcher.py의 extract_comment_info 메서드와 유사한 역할
 * - Python 코드: comment_info = { "comment_id": ..., "text_original": ..., ... }
 */
@Component
public class YoutubeCommentMapper {

    /**
     * YouTube API Comment 객체를 RedisYoutubeComment DTO로 변환
     * 
     * @param comment YouTube API Comment 객체
     * @param parentId 부모 댓글 ID (최상위 댓글이면 null, 대댓글이면 부모 ID)
     * @return RedisYoutubeComment DTO (변환 실패 시 null)
     */
    public RedisYoutubeComment toRedisComment(Comment comment, String parentId) {
        if (comment == null || comment.getSnippet() == null) {
            return null;
        }

        // 기본 필드 추출 (재사용성 향상)
        CommentBasicFields basicFields = extractBasicFields(comment);

        return RedisYoutubeComment.builder()
            .commentId(basicFields.commentId)
            .textOriginal(basicFields.textOriginal)
            .authorName(basicFields.authorName)
            .likeCount(basicFields.likeCount)
            .publishedAt(basicFields.publishedAt)
            .build();
    }
    
    /**
     * 댓글의 기본 필드 추출 (재사용성 향상)
     * 
     * @param comment YouTube API Comment 객체
     * @return 기본 필드들
     */
    private CommentBasicFields extractBasicFields(Comment comment) {
        String commentId = comment.getId();
        
        // 댓글 원본 텍스트 추출
        String textOriginal = comment.getSnippet().getTextDisplay();
        if (comment.getSnippet().getTextOriginal() != null) {
            textOriginal = comment.getSnippet().getTextOriginal();
        }
        
        // 작성자 이름 추출
        String authorName = comment.getSnippet().getAuthorDisplayName();
        
        // 좋아요 수 추출
        Long likeCount = null;
        if (comment.getSnippet().getLikeCount() != null) {
            likeCount = comment.getSnippet().getLikeCount().longValue();
        }

        // 발행 시간 변환
        String publishedAt = null;
        if (comment.getSnippet().getPublishedAt() != null) {
            publishedAt = comment.getSnippet().getPublishedAt().toStringRfc3339();
        }
        
        return new CommentBasicFields(commentId, textOriginal, authorName, likeCount, publishedAt);
    }
    
    /**
     * 댓글 기본 필드 임시 저장용 클래스
     */
    private static class CommentBasicFields {
        final String commentId;
        final String textOriginal;
        final String authorName;
        final Long likeCount;
        final String publishedAt;
        
        CommentBasicFields(String commentId, String textOriginal, String authorName, Long likeCount, String publishedAt) {
            this.commentId = commentId;
            this.textOriginal = textOriginal;
            this.authorName = authorName;
            this.likeCount = likeCount;
            this.publishedAt = publishedAt;
        }
    }
    
    /**
     * YouTube API Comment 객체를 RedisYoutubeCommentFull DTO로 변환 (증분 동기화용)
     * 
     * 전체 메타데이터를 포함하여 변환합니다:
     * - 기본 필드: comment_id, text_original, author_name, like_count, published_at
     * - 추가 필드: author_channel_id, updated_at, parent_id, total_reply_count, can_rate, viewer_rating
     * 
     * @param comment YouTube API Comment 객체
     * @param parentId 부모 댓글 ID (최상위 댓글이면 null, 대댓글이면 부모 ID)
     * @param totalReplyCount 대댓글 개수 (CommentThread에서 가져온 값, 대댓글인 경우 null)
     * @return RedisYoutubeCommentFull DTO (변환 실패 시 null)
     */
    public RedisYoutubeCommentFull toRedisCommentFull(Comment comment, String parentId, Long totalReplyCount) {
        if (comment == null || comment.getSnippet() == null) {
            return null;
        }

        // 기본 필드 추출 (재사용성 향상)
        CommentBasicFields basicFields = extractBasicFields(comment);
        
        // 추가 필드 추출 (전체 메타데이터)
        // 작성자 채널 ID 추출
        String authorChannelId = null;
        if (comment.getSnippet().getAuthorChannelId() != null) {
            authorChannelId = comment.getSnippet().getAuthorChannelId().getValue();
        }
        
        // 수정 시간 변환
        String updatedAt = null;
        if (comment.getSnippet().getUpdatedAt() != null) {
            updatedAt = comment.getSnippet().getUpdatedAt().toStringRfc3339();
        }
        
        // 평가 가능 여부 추출
        Boolean canRate = comment.getSnippet().getCanRate();
        
        // 시청자 평가 추출
        String viewerRating = null;
        if (comment.getSnippet().getViewerRating() != null) {
            viewerRating = comment.getSnippet().getViewerRating();
        }

        return RedisYoutubeCommentFull.builder()
            .commentId(basicFields.commentId)
            .textOriginal(basicFields.textOriginal)
            .authorName(basicFields.authorName)
            .authorChannelId(authorChannelId)
            .likeCount(basicFields.likeCount)
            .publishedAt(basicFields.publishedAt)
            .updatedAt(updatedAt)
            .parentId(parentId)
            .totalReplyCount(totalReplyCount)
            .canRate(canRate)
            .viewerRating(viewerRating)
            .build();
    }
}
