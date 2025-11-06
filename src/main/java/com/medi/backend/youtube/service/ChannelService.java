package com.medi.backend.youtube.service;

import java.util.List;

import com.medi.backend.youtube.dto.YoutubeChannelDto;

public interface ChannelService {
    public List<YoutubeChannelDto> getChannelsByUserId(Integer userId);
    public YoutubeChannelDto getOneChannelByIdAndUserId(Integer id, Integer userId);
    public List<YoutubeChannelDto> getAllChannelsForAdmin();

    public Integer deleteChannelById(Integer id, Integer userId);
    
}
