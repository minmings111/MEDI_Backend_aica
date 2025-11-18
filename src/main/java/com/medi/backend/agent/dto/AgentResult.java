package com.medi.backend.agent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

@Getter
public class AgentResult {

    @JsonProperty("channel_id")
    private final String channelId;
    
    // first result: video 20ea 
    @JsonProperty("video_results")
    private final List<VideoAnalysisResult> videoResults;
    
    // second result: (each channel)
    @JsonProperty("channel_results")
    private final ChannelAnalysisResult channelResults;
    
    @JsonCreator
    public AgentResult(
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("video_results") List<VideoAnalysisResult> videoResults,
        @JsonProperty("channel_results") ChannelAnalysisResult channelResults
    ) {
        this.channelId = channelId;
        this.videoResults = videoResults;
        this.channelResults = channelResults;
    }
    

    
    // video result
    @Getter
    public static class VideoAnalysisResult {
        @JsonProperty("video_id")
        private final String videoId;
        
        @JsonProperty("summarize")
        private final String summarize;
        
        @JsonProperty("communication")
        private final String communication;
        
        @JsonCreator
        public VideoAnalysisResult(
            @JsonProperty("video_id") String videoId,
            @JsonProperty("summarize") String summarize,
            @JsonProperty("communication") String communication
        ) {
            this.videoId = videoId;
            this.summarize = summarize;
            this.communication = communication;
        }
    }
    
    // channel result
    @Getter
    public static class ChannelAnalysisResult {
        @JsonProperty("profile")
        private final String profile;
        
        @JsonProperty("ecosystem")
        private final String ecosystem;
        
        @JsonCreator
        public ChannelAnalysisResult(
            @JsonProperty("profile") String profile,
            @JsonProperty("ecosystem") String ecosystem
        ) {
            this.profile = profile;
            this.ecosystem = ecosystem;
        }
    }
}