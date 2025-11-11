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
}


