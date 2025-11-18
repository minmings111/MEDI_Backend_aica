package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AgentProfileResult {
    @JsonProperty("channel_id")
    private final String channelId;
    @JsonProperty("profile_report")
    private final String profileReport;
    @JsonProperty("profiled_at")
    private final String profiledAt;
    @JsonProperty("model")
    private final String model;

    @JsonCreator
    public AgentProfileResult(
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("profile_report") String profileReport,
        @JsonProperty("profiled_at") String profiledAt,
        @JsonProperty("model") String model
    ) {
        this.channelId = channelId;
        this.profileReport = profileReport;
        this.profiledAt = profiledAt;
        this.model = model;
    }
}

