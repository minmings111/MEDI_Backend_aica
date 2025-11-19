package com.medi.backend.youtube.mapper;

import com.medi.backend.youtube.dto.YoutubeVideoDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface YoutubeVideoMapper {
    void upsert(YoutubeVideoDto video);
    YoutubeVideoDto findByYoutubeVideoId(@Param("youtubeVideoId") String youtubeVideoId);
    List<YoutubeVideoDto> findByChannelId(@Param("channelId") Integer channelId);
    
    /**
     * 비디오 ID로 YouTube 채널 ID 조회 (JOIN 쿼리)
     * Redis 메타데이터가 없을 때 MySQL fallback용
     * 
     * @param youtubeVideoId YouTube 비디오 ID
     * @return YouTube 채널 ID (UC... 형식), 없으면 null
     */
    String findYoutubeChannelIdByVideoId(@Param("youtubeVideoId") String youtubeVideoId);
}


