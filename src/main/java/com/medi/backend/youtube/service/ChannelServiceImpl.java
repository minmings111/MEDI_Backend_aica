package com.medi.backend.youtube.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.ChannelMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChannelServiceImpl implements ChannelService{

    private final ChannelMapper channelMapper;

    public ChannelServiceImpl(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<YoutubeChannelDto> getChannelsByUserId(Integer userId) {
        log.info("🔍 채널 목록 조회 시작: userId={}", userId);
        
        List<YoutubeChannelDto> channels = channelMapper.getChannelsByUserId(userId);
        
        if (channels == null) {
            log.warn("⚠️ 채널 목록 조회 결과가 null입니다: userId={}", userId);
            return List.of();
        }
        
        log.info("✅ 채널 목록 조회 완료: userId={}, 채널수={}개", userId, channels.size());
        
        if (channels.isEmpty()) {
            log.warn("⚠️ 채널 목록이 비어있습니다: userId={}", userId);
        } else {
            for (YoutubeChannelDto channel : channels) {
                log.debug("✅ 조회된 채널: channelId={}, name={}, deletedAt={}, userId={}", 
                    channel.getYoutubeChannelId(), channel.getChannelName(), 
                    channel.getDeletedAt(), channel.getUserId());
            }
        }
        
        return channels;
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
