package com.medi.backend.youtube.redis.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.medi.backend.youtube.redis.dto.RedisSyncResult;


//  YouTube data Redis synchronization unified service interface
 
//  1. get the channel list of the user from YouTube API (independently from DB)
//  2. save the top 20 video IDs of each channel to Redis
//  3. save the video metadata to Redis (after 2 is completed)
//  4. save the video comments to Redis (after 3 is completed)

public interface YoutubeRedisSyncService {
    
    // full sync process (initial sync)
    RedisSyncResult syncToRedis(Integer userId);
    
    // full sync process (initial sync) - 비동기 버전
    CompletableFuture<RedisSyncResult> syncToRedisAsync(Integer userId);
    
    // incremental sync process (incremental sync)
    // videoIds is from the previous sync process
    RedisSyncResult syncIncrementalToRedis(Integer userId, List<String> videoIds);
}

