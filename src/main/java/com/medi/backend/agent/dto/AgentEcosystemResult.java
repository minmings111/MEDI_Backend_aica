package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AgentEcosystemResult {
    @JsonProperty("channel_id")
    private final String channelId;
    @JsonProperty("ecosystem_report")
    private final String ecosystemReport;
    @JsonProperty("profiled_at")
    private final String profiledAt;
    @JsonProperty("model")
    private final String model;

    @JsonCreator
    public AgentEcosystemResult(
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("ecosystem_report") String ecosystemReport,
        @JsonProperty("profiled_at") String profiledAt,
        @JsonProperty("model") String model
    ) {
        this.channelId = channelId;
        this.ecosystemReport = ecosystemReport;
        this.profiledAt = profiledAt;
        this.model = model;
    }
}

