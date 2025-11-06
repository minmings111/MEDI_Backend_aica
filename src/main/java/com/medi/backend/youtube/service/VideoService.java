package com.medi.backend.youtube.service;

import java.util.List;

import com.medi.backend.youtube.dto.YoutubeVideoDto;

public interface VideoService {

    public List<YoutubeVideoDto> getVideosByChannelIdAndUserId(Integer channelId, Integer userId);
    public YoutubeVideoDto getVideoByIdAndUserId(Integer id, Integer userId);
    public List<YoutubeVideoDto> getVideosByUserId(Integer userId);
    public List<YoutubeVideoDto> getAllVideosForAdmin();
    
}
