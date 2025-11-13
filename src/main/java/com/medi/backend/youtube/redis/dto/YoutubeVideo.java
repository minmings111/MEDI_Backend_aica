package com.medi.backend.youtube.redis.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

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
