package com.medi.backend.youtube.redis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * YouTube 댓글 DTO (Redis 저장용)
 * 
 * Redis 저장 형식:
 * - Key: video:{video_id}:comments:json
 * - Type: String (JSON 배열)
 * - Value: [{comment_id: "...", text_original: "...", ...}, ...]
 * 
 * 필드명 규칙:
 * - AI 서버(Python/TypeScript)와의 호환성을 위해 스네이크 케이스 사용
 * - JSON 직렬화 시 @JsonProperty로 명시된 이름으로 변환됨
 * 
 * 예시:
 * {
 *   "comment_id": "UgyQnoD1JS_mILywmB94AaABAg",
 *   "text_original": "'이경민' 이라는 사람 다시본다",
 *   "author_name": "@user123",
 *   "like_count": 105,
 *   "published_at": "2021-04-18T10:05:00Z"
 * }
 */
@Getter
@Builder
public class YoutubeComment {
    @JsonProperty("comment_id")
    private final String commentId;
    
    @JsonProperty("text_original")
    private final String textOriginal;
    
    @JsonProperty("author_name")
    private final String authorName;
    
    @JsonProperty("like_count")
    private final Long likeCount;
    
    @JsonProperty("published_at")
    private final String publishedAt;  // ISO 8601 형식 문자열 (예: "2021-04-18T10:05:00Z")

    @JsonCreator
    public YoutubeComment(
        @JsonProperty("comment_id") String commentId,
        @JsonProperty("text_original") String textOriginal,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("like_count") Long likeCount,
        @JsonProperty("published_at") String publishedAt
    ) {
        this.commentId = commentId;
        this.textOriginal = textOriginal;
        this.authorName = authorName;
        this.likeCount = likeCount;
        this.publishedAt = publishedAt;
    }
}
