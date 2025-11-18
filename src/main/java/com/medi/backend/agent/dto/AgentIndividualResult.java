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
 * 
 * 메타데이터 필드:
 * - video_title: summary JSON에만 포함 (비디오 제목)
 * - summarized_at: summary JSON에만 포함 (요약 생성 시간)
 * - char_count: summary JSON에만 포함 (문자 수)
 * - profiled_at: profile_report, ecosystem_report JSON에 포함 (프로파일 생성 시간)
 * - model: summary, profile_report, ecosystem_report JSON에 포함 (AI 모델 버전)
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
    
    // 메타데이터 필드
    @JsonProperty("video_title")
    private final String videoTitle;  // summary JSON에만 포함 (null 가능)
    
    @JsonProperty("summarized_at")
    private final String summarizedAt;  // summary JSON에만 포함 (null 가능)
    
    @JsonProperty("char_count")
    private final Integer charCount;  // summary JSON에만 포함 (null 가능)
    
    @JsonProperty("profiled_at")
    private final String profiledAt;  // profile_report, ecosystem_report JSON에 포함 (null 가능)
    
    @JsonProperty("model")
    private final String model;  // summary, profile_report, ecosystem_report JSON에 포함 (null 가능)
    
    @JsonCreator
    public AgentIndividualResult(
        @JsonProperty("video_id") String videoId,
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("summary") String summary,
        @JsonProperty("communication_report") String communicationReport,
        @JsonProperty("profile_report") String profileReport,
        @JsonProperty("ecosystem_report") String ecosystemReport,
        @JsonProperty("video_title") String videoTitle,
        @JsonProperty("summarized_at") String summarizedAt,
        @JsonProperty("char_count") Integer charCount,
        @JsonProperty("profiled_at") String profiledAt,
        @JsonProperty("model") String model
    ) {
        this.videoId = videoId;
        this.channelId = channelId;
        this.summary = summary;
        this.communicationReport = communicationReport;
        this.profileReport = profileReport;
        this.ecosystemReport = ecosystemReport;
        this.videoTitle = videoTitle;
        this.summarizedAt = summarizedAt;
        this.charCount = charCount;
        this.profiledAt = profiledAt;
        this.model = model;
    }
}

