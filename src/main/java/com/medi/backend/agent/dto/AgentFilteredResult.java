package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;



// same the Redis's RedisYoutubeCommentFull + video_id(key) + status

@Getter
public class AgentFilteredResult {
    @JsonProperty("video_id")
    private final String videoId;
    
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
    private final String publishedAt;
    
    @JsonProperty("updated_at")
    private final String updatedAt;
    
    @JsonProperty("parent_id")
    private final String parentId;
    
    @JsonProperty("total_reply_count")
    private final Long totalReplyCount;
    
    @JsonProperty("can_rate")
    private final Boolean canRate;
    
    @JsonProperty("viewer_rating")
    private final String viewerRating;
    
    @JsonProperty("status")
    private final String status;  // "normal", "filtered", "content_suggestion"
    
    @JsonProperty("reason")
    private final String reason;  // 필터링 이유 (예: "hate_speech", "auto_detected")

    @JsonCreator
    public AgentFilteredResult(
        @JsonProperty("video_id") String videoId,
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
        @JsonProperty("viewer_rating") String viewerRating,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason
    ) {
        this.videoId = videoId;
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
        this.status = status;
        this.reason = reason;
    }
}

// ex
// [
//   {
//     "video_id": "td7kfwpTDcA",
//     "comment_id": "UgyQbxEOl74hRc1xkQl4AaABAg",
//     "text_original": "댓글 내용...",
//     "author_name": "작성자명",
//     "author_channel_id": "UC...",
//     "like_count": 5,
//     "published_at": "2025-01-18T10:30:00Z",
//     "updated_at": "2025-01-18T11:00:00Z",
//     "parent_id": null,
//     "total_reply_count": 2,
//     "can_rate": true,
//     "viewer_rating": "like",
//     "status": "filtered",
//     "reason": "hate_speech"
//   },
//   {
//     "video_id": "td7kfwpTDcA",
//     "comment_id": "ADFjYOKUh-sADa-V9q-trq",
//     "text_original": "다른 댓글...",
//     "author_name": "다른 작성자",
//     "author_channel_id": "UC...",
//     "like_count": 10,
//     "published_at": "2025-01-18T11:00:00Z",
//     "updated_at": null,
//     "parent_id": null,
//     "total_reply_count": 0,
//     "can_rate": true,
//     "viewer_rating": "none",
//     "status": "content_suggestion",
//     "reason": "auto_detected"
//   }
// ]