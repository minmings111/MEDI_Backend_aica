package com.medi.backend.youtube.event;

import lombok.Getter;
import lombok.ToString;
import java.time.LocalDateTime;

/**
 * 영상 캐시 이벤트
 * MySQL 커밋 후 Redis 캐시 업데이트를 위한 이벤트
 */
@Getter
@ToString
public class VideoCacheEvent {
    private final String youtubeVideoId;
    private final String title;
    private final String thumbnailUrl;
    private final LocalDateTime publishedAt;

    public VideoCacheEvent(String youtubeVideoId, String title, 
                          String thumbnailUrl, LocalDateTime publishedAt) {
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
    }
}

