package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.util.List;

@Getter
public class AgentFilteredCommentsRequest {
    @JsonProperty("video_id")
    private final String videoId;
    
    @JsonProperty("comments")
    private final List<CommentData> comments;
    
    @JsonProperty("total")
    private final Integer total;
    
    @JsonProperty("analyzed_at")
    private final String analyzedAt;
    
    @JsonCreator
    public AgentFilteredCommentsRequest(
        @JsonProperty("video_id") String videoId,
        @JsonProperty("comments") List<CommentData> comments,
        @JsonProperty("total") Integer total,
        @JsonProperty("analyzed_at") String analyzedAt
    ) {
        this.videoId = videoId;
        this.comments = comments;
        this.total = total;
        this.analyzedAt = analyzedAt;
    }
    
    @Getter
    public static class CommentData {
        @JsonProperty("comment_id")
        private final String commentId;
        
        @JsonProperty("text_original")
        private final String textOriginal;
        
        @JsonProperty("author_name")
        private final String authorName;
        
        @JsonProperty("like_count")
        private final Long likeCount;
        
        @JsonProperty("published_at")
        private final String publishedAt;
        
        @JsonProperty("reason")
        private final String reason;  // optional: "필터링 필요" 등
        
        @JsonCreator
        public CommentData(
            @JsonProperty("comment_id") String commentId,
            @JsonProperty("text_original") String textOriginal,
            @JsonProperty("author_name") String authorName,
            @JsonProperty("like_count") Long likeCount,
            @JsonProperty("published_at") String publishedAt,
            @JsonProperty("reason") String reason
        ) {
            this.commentId = commentId;
            this.textOriginal = textOriginal;
            this.authorName = authorName;
            this.likeCount = likeCount;
            this.publishedAt = publishedAt;
            this.reason = reason;
        }
    }
}

