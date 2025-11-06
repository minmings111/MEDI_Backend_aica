package com.medi.backend.youtube.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.ChannelMapper;

@Service
public class ChannelServiceImpl implements ChannelService{

    private final ChannelMapper channelMapper;

    public ChannelServiceImpl(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeChannelDto> getChannelsByUserId(Integer userId) {
        
        return channelMapper.getChannelsByUserId(userId);
    }


    @Override
    @Transactional(readOnly = true)
    public YoutubeChannelDto getOneChannelByIdAndUserId(Integer id, Integer userId) {
        return channelMapper.getOneChannelByIdAndUserId(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeChannelDto> getAllChannelsForAdmin() {
        return channelMapper.getAllChannelsForAdmin();
    }




    @Override
    @Transactional
    public Integer deleteChannelById(Integer id, Integer userId) {
        return channelMapper.deleteChannelById(id, userId);
    }

    
    
}
