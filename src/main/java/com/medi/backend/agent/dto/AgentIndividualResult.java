package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

/**
 * AI 서버가 보내는 개별 분석 결과 DTO
 * 
 * Redis Key 구조:
 * - video:{video_id}:summary → summary 필드
 * - video:{video_id}:result:communication → communication_report 필드
 * - channel:{channel_id}:profile → profile_report 필드
 * - channel:{channel_id}:result:comment_ecosystem → ecosystem_report 필드
 */
@Getter
public class AgentIndividualResult {
    
    @JsonProperty("video_id")
    private final String videoId;  // 비디오 결과인 경우 (null 가능)
    
    @JsonProperty("channel_id")
    private final String channelId;  // 채널 결과인 경우 (null 가능)
    
    @JsonProperty("summary")
    private final String summary;  // video:{video_id}:summary 결과
    
    @JsonProperty("communication_report")
    private final String communicationReport;  // video:{video_id}:result:communication 결과
    
    @JsonProperty("profile_report")
    private final String profileReport;  // channel:{channel_id}:profile 결과
    
    @JsonProperty("ecosystem_report")
    private final String ecosystemReport;  // channel:{channel_id}:result:comment_ecosystem 결과
    
    @JsonCreator
    public AgentIndividualResult(
        @JsonProperty("video_id") String videoId,
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("summary") String summary,
        @JsonProperty("communication_report") String communicationReport,
        @JsonProperty("profile_report") String profileReport,
        @JsonProperty("ecosystem_report") String ecosystemReport
    ) {
        this.videoId = videoId;
        this.channelId = channelId;
        this.summary = summary;
        this.communicationReport = communicationReport;
        this.profileReport = profileReport;
        this.ecosystemReport = ecosystemReport;
    }
}

