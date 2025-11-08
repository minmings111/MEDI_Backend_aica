package com.medi.backend.youtube.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 채널 캐시 이벤트
 * MySQL 커밋 후 Redis 캐시 업데이트를 위한 이벤트
 */
@Getter
@ToString
public class ChannelCacheEvent {
    private final String youtubeChannelId;
    private final String channelName;
    private final String channelHandle;
    private final String thumbnailUrl;
    private final Integer userId;

    public ChannelCacheEvent(String youtubeChannelId, String channelName, 
                             String channelHandle, String thumbnailUrl, Integer userId) {
        this.youtubeChannelId = youtubeChannelId;
        this.channelName = channelName;
        this.channelHandle = channelHandle;
        this.thumbnailUrl = thumbnailUrl;
        this.userId = userId;
    }
}

