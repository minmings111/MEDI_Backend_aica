package com.medi.backend.youtube.redis.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class YoutubeComment {
    private final String commentId;
    private final String parentId;
    private final String text;
    private final String author;
    private final String authorChannelId;  // 작성자 채널 ID (선택적, null 가능)
    private final Long likeCount;          // 좋아요 수 (null 가능)
    private final LocalDateTime publishedAt;
    private final LocalDateTime updatedAt;  // 수정 시간 (null 가능)

    @JsonCreator
    public YoutubeComment(
        @JsonProperty("commentId") String commentId,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("text") String text,
        @JsonProperty("author") String author,
        @JsonProperty("authorChannelId") String authorChannelId,
        @JsonProperty("likeCount") Long likeCount,
        @JsonProperty("publishedAt") LocalDateTime publishedAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
    ) {
        this.commentId = commentId;
        this.parentId = parentId;
        this.text = text;
        this.author = author;
        this.authorChannelId = authorChannelId;
        this.likeCount = likeCount;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
    }
}
