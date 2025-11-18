package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AgentSummaryResult {
    @JsonProperty("video_id")
    private final String videoId;
    @JsonProperty("video_title")
    private final String videoTitle;
    @JsonProperty("summary")
    private final String summary;
    @JsonProperty("summarized_at")
    private final String summarizedAt;
    @JsonProperty("char_count")
    private final Integer charCount;
    @JsonProperty("model")
    private final String model;

    @JsonCreator
    public AgentSummaryResult(
        @JsonProperty("video_id") String videoId,
        @JsonProperty("video_title") String videoTitle,
        @JsonProperty("summary") String summary,
        @JsonProperty("summarized_at") String summarizedAt,
        @JsonProperty("char_count") Integer charCount,
        @JsonProperty("model") String model
    ) {
        this.videoId = videoId;
        this.videoTitle = videoTitle;
        this.summary = summary;
        this.summarizedAt = summarizedAt;
        this.charCount = charCount;
        this.model = model;
    }
}

