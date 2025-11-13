package com.medi.backend.youtube.redis.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * YouTube 비디오 DTO (내부 사용)
 * 
 * 역할:
 * - YouTube API에서 가져온 비디오 정보를 담는 내부용 DTO
 * - 이 DTO 자체는 Redis에 직접 저장되지 않음
 * 
 * Redis 저장 방식:
 * - YoutubeVideoServiceImpl에서 Map을 직접 만들어 필요한 필드만 선택하여 저장
 * - 저장 위치: saveVideoMetadataToRedis() 메서드
 * - 저장 형식: video:{video_id}:meta:json
 * 
 * 참고:
 * - YoutubeComment와 달리 @JsonProperty가 없는 이유:
 *   이 DTO는 JSON으로 직접 직렬화되지 않고, Map을 통해 간접적으로 저장됨
 */
@Getter
@Builder
public class YoutubeVideo {
    private final String youtubeVideoId;
    private final String title;
    private final String thumbnailUrl;
    private final LocalDateTime publishedAt;
    private final Long viewCount;
    private final Long likeCount;
    private final Long commentCount;
    private final String channelId;        // 채널 ID (Python 코드의 channel_id)
    private final List<String> tags;      // 비디오 태그 리스트 (Python 코드의 video_tags)
}
