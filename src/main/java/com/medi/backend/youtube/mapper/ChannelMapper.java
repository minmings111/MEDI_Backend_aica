package com.medi.backend.youtube.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.youtube.dto.YoutubeChannelDto;

@Mapper
public interface ChannelMapper {
    
    public List<YoutubeChannelDto> getChannelsByUserId(Integer userId);
    public YoutubeChannelDto getOneChannelByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);
    
    /**
     * 채널 ID로 채널 정보 조회 (관리자용, userId 체크 없음)
     */
    public YoutubeChannelDto getOneChannelById(@Param("id") Integer id);
    
    public List<YoutubeChannelDto> getAllChannelsForAdmin();

    public Integer deleteChannelById(@Param("id") Integer id, @Param("userId") Integer userId);
    
    
}
