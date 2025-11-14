package com.medi.backend.youtube.redis.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * YouTube 비디오 DTO (Redis 저장용)
 * 
 * Redis 저장 형식:
 * - Key: video:{video_id}:meta:json
 * - Type: String (JSON)
 * - Value: {channel_id, video_id, video_title, video_tags}
 * 
 * 필드명 규칙:
 * - AI 서버(Python/TypeScript)와의 호환성을 위해 스네이크 케이스 사용
 * - JSON 직렬화 시 @JsonProperty로 명시된 이름으로 변환됨
 * 
 * 예시:
 * {
 *   "channel_id": "UCBA9XaL5wCdHnC5EmEzwrqw",
 *   "video_id": "td7kfwpTDcA",
 *   "video_title": "시작보다 어려운 끝 [츠예나, 이경민]",
 *   "video_tags": ["김민교", "츠예나", "이경민", "산본포차"]
 * }
 */
@Getter
@Builder
public class RedisYoutubeVideo {
    @JsonProperty("video_id")
    private final String youtubeVideoId;
    
    @JsonProperty("video_title")
    private final String title;
    
    @JsonProperty("channel_id")
    private final String channelId;
    
    @JsonProperty("video_tags")
    private final List<String> tags;

    @JsonCreator
    public RedisYoutubeVideo(
        @JsonProperty("video_id") String youtubeVideoId,
        @JsonProperty("video_title") String title,
        @JsonProperty("channel_id") String channelId,
        @JsonProperty("video_tags") List<String> tags
    ) {
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.channelId = channelId;
        this.tags = tags;
    }
}
