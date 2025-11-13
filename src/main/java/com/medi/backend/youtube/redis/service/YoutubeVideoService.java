package com.medi.backend.youtube.redis.service;

import java.util.List;
import java.util.Map;

import com.medi.backend.youtube.redis.dto.YoutubeVideo;

public interface YoutubeVideoService {
    
    /**
     * 각 채널마다 조회수 상위 20개 영상을 조회
     * 
     * @param userId 사용자 ID
     * @return 채널 ID를 키로 하고, 해당 채널의 조회수 상위 20개 영상 리스트를 값으로 하는 Map
     */
    Map<String, List<YoutubeVideo>> getTop20VideosByChannel(Integer userId);
}
