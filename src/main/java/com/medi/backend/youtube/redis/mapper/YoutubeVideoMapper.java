package com.medi.backend.youtube.redis.mapper;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.api.services.youtube.model.Video;
import com.medi.backend.youtube.redis.dto.YoutubeVideo;

@Component
public class YoutubeVideoMapper {

    /**
     * YouTube API Video 객체를 YoutubeVideo DTO로 변환
     * 
     * @param video YouTube API Video 객체
     * @param channelId 채널 ID (Python 코드의 channel_id)
     * @return YoutubeVideo DTO
     */
    public YoutubeVideo toRedisVideo(Video video, String channelId) {
        if (video == null) {
            return null;
        }

        String videoId = video.getId();
        String title = video.getSnippet() != null ? video.getSnippet().getTitle() : null;
        
        String thumbnailUrl = null;
        if (video.getSnippet() != null && video.getSnippet().getThumbnails() != null 
            && video.getSnippet().getThumbnails().getDefault() != null) {
            thumbnailUrl = video.getSnippet().getThumbnails().getDefault().getUrl();
        }

        java.time.LocalDateTime publishedAt = null;
        if (video.getSnippet() != null && video.getSnippet().getPublishedAt() != null) {
            publishedAt = ZonedDateTime.parse(video.getSnippet().getPublishedAt().toStringRfc3339())
                .toLocalDateTime();
        }

        Long viewCount = null;
        Long likeCount = null;
        Long commentCount = null;
        if (video.getStatistics() != null) {
            viewCount = video.getStatistics().getViewCount() != null 
                ? video.getStatistics().getViewCount().longValue() : null;
            likeCount = video.getStatistics().getLikeCount() != null 
                ? video.getStatistics().getLikeCount().longValue() : null;
            commentCount = video.getStatistics().getCommentCount() != null 
                ? video.getStatistics().getCommentCount().longValue() : null;
        }

        // 태그 추출 (Python 코드의 video_tags)
        List<String> tags = null;
        if (video.getSnippet() != null && video.getSnippet().getTags() != null) {
            tags = video.getSnippet().getTags();
        }

        return YoutubeVideo.builder()
            .youtubeVideoId(videoId)
            .title(title)
            .thumbnailUrl(thumbnailUrl)
            .publishedAt(publishedAt)
            .viewCount(viewCount)
            .likeCount(likeCount)
            .commentCount(commentCount)
            .channelId(channelId)      // Python 코드의 channel_id
            .tags(tags)                // Python 코드의 video_tags
            .build();
    }
}
