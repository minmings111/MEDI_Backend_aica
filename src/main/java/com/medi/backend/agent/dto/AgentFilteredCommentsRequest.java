package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.util.List;

@Getter
public class AgentFilteredCommentsRequest {
    @JsonProperty("channelId")
    private final String channelId;
    
    @JsonProperty("videoId")
    private final String videoId;
    
    @JsonProperty("analysisTimestamp")
    private final String analysisTimestamp;
    
    @JsonProperty("filteredComments")
    private final List<CommentData> filteredComments;
    
    @JsonProperty("contentSuggestions")
    private final List<CommentData> contentSuggestions;
    
    @JsonProperty("sentimentStats")
    private final SentimentStats sentimentStats;
    
    @JsonProperty("riskSummary")
    private final String riskSummary;
    
    @JsonCreator
    public AgentFilteredCommentsRequest(
        @JsonProperty("channelId") String channelId,
        @JsonProperty("videoId") String videoId,
        @JsonProperty("analysisTimestamp") String analysisTimestamp,
        @JsonProperty("filteredComments") List<CommentData> filteredComments,
        @JsonProperty("contentSuggestions") List<CommentData> contentSuggestions,
        @JsonProperty("sentimentStats") SentimentStats sentimentStats,
        @JsonProperty("riskSummary") String riskSummary
    ) {
        this.channelId = channelId;
        this.videoId = videoId;
        this.analysisTimestamp = analysisTimestamp;
        this.filteredComments = filteredComments;
        this.contentSuggestions = contentSuggestions;
        this.sentimentStats = sentimentStats;
        this.riskSummary = riskSummary;
    }
    
    // 기존 코드와의 호환성을 위한 메서드들
    public String getAnalyzedAt() {
        return analysisTimestamp;
    }
    
    public List<CommentData> getComments() {
        // filteredComments와 contentSuggestions를 합쳐서 반환
        List<CommentData> allComments = new java.util.ArrayList<>();
        if (filteredComments != null) allComments.addAll(filteredComments);
        if (contentSuggestions != null) allComments.addAll(contentSuggestions);
        return allComments;
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
        private final String reason;  // filteredComments에만 있음
        
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
    
    @Getter
    public static class SentimentStats {
        @JsonProperty("neutral")
        private final Integer neutral;
        
        @JsonProperty("filtered")
        private final Integer filtered;
        
        @JsonProperty("suggestion")
        private final Integer suggestion;
        
        @JsonCreator
        public SentimentStats(
            @JsonProperty("neutral") Integer neutral,
            @JsonProperty("filtered") Integer filtered,
            @JsonProperty("suggestion") Integer suggestion
        ) {
            this.neutral = neutral;
            this.filtered = filtered;
            this.suggestion = suggestion;
        }
    }
}

