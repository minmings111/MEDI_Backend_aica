package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AgentCommunicationResult {
    @JsonProperty("video_id")
    private final String videoId;
    @JsonProperty("communication_report")
    private final String communicationReport;
    @JsonProperty("model")
    private final String model;

    @JsonCreator
    public AgentCommunicationResult(
        @JsonProperty("video_id") String videoId,
        @JsonProperty("communication_report") String communicationReport,
        @JsonProperty("model") String model
    ) {
        this.videoId = videoId;
        this.communicationReport = communicationReport;
        this.model = model;
    }
}

