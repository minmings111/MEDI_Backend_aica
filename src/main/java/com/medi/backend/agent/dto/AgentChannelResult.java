package com.medi.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import lombok.Getter;

/**
 * AI 서버가 보내는 채널별 통합 분석 결과 DTO
 * 
 * 채널별로 모든 비디오 결과와 채널 결과가 하나의 JSON으로 묶여서 전달됨
 * 
 * JSON 예시:
 * {
 *   "channel_id": "UCEYB_U1tEPwueg4GIaivioQ",
 *   "video_results": [
 *     {
 *       "video_id": "jdNuhFhLP_c",
 *       "summary": "**1. 핵심 내용 요약...**",
 *       "communication_report": "**영상-댓글 반응 매핑...**",
 *       "video_title": "발로란트 \"모든 캐릭터\"...",
 *       "summarized_at": "2025-11-18T11:56:38.053884",
 *       "char_count": 941,
 *       "model": "gpt-4o-mini"
 *     },
 *     ...
 *   ],
 *   "profile_report": "## 1. IDENTITY_DESCRIPTOR...",
 *   "ecosystem_report": "## 1. BASELINE_TONE...",
 *   "profiled_at": "2025-11-18T11:59:35.588471",
 *   "model": "gpt-4o"
 * }
 */
@Getter
public class AgentChannelResult {

    @JsonProperty("channel_id")
    private final String channelId;

    @JsonProperty("video_results")
    private final List<VideoResult> videoResults;

    @JsonProperty("profile_report")
    private final String profileReport;

    @JsonProperty("ecosystem_report")
    private final String ecosystemReport;

    @JsonProperty("profiled_at")
    private final String profiledAt;

    @JsonProperty("model")
    private final String model;

    @JsonCreator
    public AgentChannelResult(
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("video_results") List<VideoResult> videoResults,
        @JsonProperty("profile_report") String profileReport,
        @JsonProperty("ecosystem_report") String ecosystemReport,
        @JsonProperty("profiled_at") String profiledAt,
        @JsonProperty("model") String model
    ) {
        this.channelId = channelId;
        this.videoResults = videoResults;
        this.profileReport = profileReport;
        this.ecosystemReport = ecosystemReport;
        this.profiledAt = profiledAt;
        this.model = model;
    }

    /**
     * 비디오별 분석 결과 내부 클래스
     */
    @Getter
    public static class VideoResult {
        @JsonProperty("video_id")
        private final String videoId;

        @JsonProperty("summary")
        private final String summary;

        @JsonProperty("communication_report")
        private final String communicationReport;

        @JsonProperty("video_title")
        private final String videoTitle;

        @JsonProperty("summarized_at")
        private final String summarizedAt;

        @JsonProperty("char_count")
        private final Integer charCount;

        @JsonProperty("model")
        private final String model;

        @JsonCreator
        public VideoResult(
            @JsonProperty("video_id") String videoId,
            @JsonProperty("summary") String summary,
            @JsonProperty("communication_report") String communicationReport,
            @JsonProperty("video_title") String videoTitle,
            @JsonProperty("summarized_at") String summarizedAt,
            @JsonProperty("char_count") Integer charCount,
            @JsonProperty("model") String model
        ) {
            this.videoId = videoId;
            this.summary = summary;
            this.communicationReport = communicationReport;
            this.videoTitle = videoTitle;
            this.summarizedAt = summarizedAt;
            this.charCount = charCount;
            this.model = model;
        }
    }
}

