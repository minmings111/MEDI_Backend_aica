package com.medi.backend.youtube.event;

import java.time.LocalDateTime;

import com.medi.backend.youtube.model.VideoSyncMode;

import lombok.Getter;
import lombok.ToString;

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
    private final VideoSyncMode syncMode;

    public VideoCacheEvent(String youtubeVideoId, String title, 
                          String thumbnailUrl, LocalDateTime publishedAt,
                           VideoSyncMode syncMode) {
        this.youtubeVideoId = youtubeVideoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;
        this.syncMode = syncMode;
    }
}

