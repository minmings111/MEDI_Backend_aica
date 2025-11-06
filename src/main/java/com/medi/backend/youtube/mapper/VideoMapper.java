package com.medi.backend.youtube.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.youtube.dto.YoutubeVideoDto;

@Mapper
public interface VideoMapper {

    public List<YoutubeVideoDto> getVideosByChannelIdAndUserId(@Param("channelId") Integer channelId);
    public YoutubeVideoDto getVideoById(Integer id);
    public YoutubeVideoDto getVideoByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);
    public List<YoutubeVideoDto> getVideosByUserId(Integer userId);
    public List<YoutubeVideoDto> getAllVideosForAdmin();
    
}
