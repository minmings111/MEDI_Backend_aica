package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;



// same the Redis's RedisYoutubeCommentFull + video_id(key)

@Getter
public class AgentFilteredComment {
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

    @JsonCreator
    public AgentFilteredComment(
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
        @JsonProperty("viewer_rating") String viewerRating
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
    }
}
