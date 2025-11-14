package com.medi.backend.youtube.redis.service;

import java.util.List;
import java.util.Map;

import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;

public interface YoutubeVideoService {
    
    /**
     * 각 채널마다 조회수 상위 20개 영상을 조회 (초기 동기화용)
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param channelIds 채널 ID 리스트 (DB 독립적)
     * @return 채널 ID를 키로 하고, 해당 채널의 조회수 상위 20개 영상 리스트를 값으로 하는 Map
     */
    Map<String, List<RedisYoutubeVideo>> getTop20VideosByChannel(Integer userId, List<String> channelIds);
    
    /**
     * 특정 비디오들의 메타데이터를 조회하여 Redis에 저장 (증분 동기화용)
     * 
     * 주의: 초기 동기화와 증분 동기화 모두 기본 메타데이터만 저장합니다.
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videoIds 비디오 ID 리스트
     * @param options 동기화 옵션 (사용하지 않음, 기본 메타데이터만 저장)
     * @return 저장된 비디오 개수
     */
    int syncVideoMetadata(Integer userId, List<String> videoIds, SyncOptions options);
}
