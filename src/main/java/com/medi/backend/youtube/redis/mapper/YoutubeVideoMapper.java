package com.medi.backend.youtube.redis.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.google.api.services.youtube.model.Video;
import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;

@Component("redisYoutubeVideoMapper")
public class YoutubeVideoMapper {
    // YouTube API Video object -> YoutubeVideo DTO

    /**
     * 기본 메타데이터만 포함하는 DTO로 변환 (초기 동기화용)
     * 
     * @param video YouTube API Video 객체
     * @param channelId 채널 ID
     * @return RedisYoutubeVideo DTO (기본 필드만)
     */
    public RedisYoutubeVideo toRedisVideo(Video video, String channelId) {
        if (video == null) {
            return null;
        }

        // video_id
        String videoId = video.getId();
        
        // video_title
        String title = video.getSnippet() != null ? video.getSnippet().getTitle() : null;

        // video_tags
        List<String> tags = null;
        if (video.getSnippet() != null && video.getSnippet().getTags() != null) {
            tags = video.getSnippet().getTags();
        }

        return RedisYoutubeVideo.builder()
            .youtubeVideoId(videoId)
            .title(title)
            .channelId(channelId)
            .tags(tags)
            .build();
    }
}
