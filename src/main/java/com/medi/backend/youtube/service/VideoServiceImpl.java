package com.medi.backend.youtube.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.mapper.ChannelMapper;
import com.medi.backend.youtube.mapper.VideoMapper;

@Service
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;
    private final ChannelMapper channelMapper;

    public VideoServiceImpl(VideoMapper videoMapper, ChannelMapper channelMapper) {
        this.videoMapper = videoMapper;
        this.channelMapper = channelMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeVideoDto> getVideosByChannelIdAndUserId(Integer channelId, Integer userId) {

        YoutubeChannelDto youtubeChannelDto = channelMapper.getOneChannelByIdAndUserId(channelId, userId);
        // If it is not the user's channel, return an empty list.
        // so that it is impossible to know whether the channel exists or not.
        if (youtubeChannelDto == null) {
            return Collections.emptyList();
        }

        return videoMapper.getVideosByChannelIdAndUserId(channelId);
    }

    @Override
    @Transactional(readOnly = true)
    public YoutubeVideoDto getVideoByIdAndUserId(Integer id, Integer userId) {
        return videoMapper.getVideoByIdAndUserId(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeVideoDto> getVideosByUserId(Integer userId) {
        return videoMapper.getVideosByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeVideoDto> getAllVideosForAdmin() {
        return videoMapper.getAllVideosForAdmin();
    }
    
}
