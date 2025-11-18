package com.medi.backend.youtube.redis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * YouTube 댓글 전체 메타데이터 DTO (Redis 저장용, 증분 동기화용)
 * 
 * Redis 저장 형식:
 * - 증분 동기화: Key: video:{video_id}:comments (원본 데이터, 절대 수정 금지)
 *                Type: Hash
 *                Field: comment_id, Value: JSON 문자열 (전체 메타데이터)
 * 
 * 초기 동기화 vs 증분 동기화:
 * - 초기 동기화: RedisYoutubeComment 사용 (기본 필드만) → comments:init (String, JSON 배열)
 * - 증분 동기화: RedisYoutubeCommentFull 사용 (전체 메타데이터) → comments (Hash)
 * 
 * 필터링 결과:
 * - FastAPI agent가 video:{video_id}:classification (Hash)에 별도로 저장
 * - 원본 데이터(video:{video_id}:comments)는 절대 수정하지 않음
 */
@Getter
@Builder
public class RedisYoutubeCommentFull {
    @JsonProperty("comment_id")
    private final String commentId;
    
    @JsonProperty("text_original")
    private final String textOriginal;
    
    @JsonProperty("author_name")
    private final String authorName;
    
    @JsonProperty("author_channel_id")
    private final String authorChannelId;
    
    @JsonProperty("like_count")
    private final Long likeCount;
    
    @JsonProperty("published_at")
    private final String publishedAt;  // ISO 8601 형식 문자열 (예: "2021-04-18T10:05:00Z")
    
    @JsonProperty("updated_at")
    private final String updatedAt;  // ISO 8601 형식 문자열 (수정 시간)
    
    @JsonProperty("parent_id")
    private final String parentId;  // 대댓글인 경우 부모 댓글 ID, 최상위 댓글이면 null
    
    @JsonProperty("total_reply_count")
    private final Long totalReplyCount;  // 대댓글 개수
    
    @JsonProperty("can_rate")
    private final Boolean canRate;  // 평가 가능 여부
    
    @JsonProperty("viewer_rating")
    private final String viewerRating;  // 시청자 평가 ("like", "none", "dislike")

    @JsonCreator
    public RedisYoutubeCommentFull(
        @JsonProperty("comment_id") String commentId,
        @JsonProperty("text_original") String textOriginal,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("author_channel_id") String authorChannelId,
        @JsonProperty("like_count") Long likeCount,
        @JsonProperty("published_at") String publishedAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("parent_id") String parentId,
        @JsonProperty("total_reply_count") Long totalReplyCount,
        @JsonProperty("can_rate") Boolean canRate,
        @JsonProperty("viewer_rating") String viewerRating
    ) {
        this.commentId = commentId;
        this.textOriginal = textOriginal;
        this.authorName = authorName;
        this.authorChannelId = authorChannelId;
        this.likeCount = likeCount;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
        this.parentId = parentId;
        this.totalReplyCount = totalReplyCount;
        this.canRate = canRate;
        this.viewerRating = viewerRating;
    }
}

