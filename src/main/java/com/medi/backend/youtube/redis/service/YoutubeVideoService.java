package com.medi.backend.youtube.redis.service;

import java.util.List;
import java.util.Map;

import com.google.api.services.youtube.YouTube;
import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;

public interface YoutubeVideoService {

    // for initial sync
    Map<String, List<RedisYoutubeVideo>> getTop10VideosByChannel(YouTube yt, List<String> channelIds);

    // for incremental sync
    int syncVideoMetadata(Integer userId, List<String> videoIds, SyncOptions options);
}
