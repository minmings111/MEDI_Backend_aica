package com.medi.backend.agent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentChannelResult;
import com.medi.backend.agent.mapper.AgentMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService{

    private final AgentMapper agentMapper; // for DB
    private final StringRedisTemplate stringRedisTemplate; // for Redis
    
    public AgentServiceImpl(AgentMapper agentMapper, StringRedisTemplate stringRedisTemplate) {
        this.agentMapper = agentMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    // utility methods for DB and Redis
    @Override
    @Transactional(readOnly = true)
    public Integer findVideoIdByYoutubeVideoId(String youtubeVideoId) {
        return agentMapper.findVideoIdByYoutubeVideoId(youtubeVideoId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findChannelIdByYoutubeChannelId(String youtubeChannelId) {
        return agentMapper.findChannelIdByYoutubeChannelId(youtubeChannelId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findUserIdByChannelId(Integer channelId) {
        return agentMapper.findUserIdByChannelId(channelId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findChannelIdByVideoId(Integer videoId) {
        return agentMapper.findChannelIdByVideoId(videoId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public String findYoutubeChannelIdByChannelId(Integer channelId) {
        return agentMapper.findYoutubeChannelIdByChannelId(channelId);
    }

    // 채널별 통합 분석 결과 저장
    @Override
    @Transactional
    public void saveChannelResult(AgentChannelResult result) {
        // 1. YouTube channel_id → 내부 channel_id 변환
        Integer channelId = findChannelIdByYoutubeChannelId(result.getChannelId());
        if (channelId == null) {
            log.warn("Channel not found: {}", result.getChannelId());
            return;
        }
        
        // 2. Redis 저장
        saveChannelResultToRedis(result);
        
        // 3. SQL 저장
        // 3-1. 비디오별 결과 저장
        if (result.getVideoResults() != null) {
            for (AgentChannelResult.VideoResult videoResult : result.getVideoResults()) {
                Integer videoId = findVideoIdByYoutubeVideoId(videoResult.getVideoId());
                if (videoId == null) {
                    log.warn("Video not found: {}", videoResult.getVideoId());
                    continue;
                }
                
                agentMapper.insertResultVideo(
                    videoId,
                    videoResult.getSummary(),
                    videoResult.getCommunicationReport(),
                    videoResult.getVideoTitle(),
                    videoResult.getSummarizedAt(),
                    videoResult.getCharCount(),
                    videoResult.getModel()
                );
            }
        }
        
        // 3-2. 채널별 결과 저장
        agentMapper.insertResultChannel(
            channelId,
            result.getProfileReport(),
            result.getEcosystemReport(),
            result.getProfiledAt(),
            result.getModel()
        );
        
        log.info("Saved channel analysis results for channel: {} ({} videos)", 
            result.getChannelId(), 
            result.getVideoResults() != null ? result.getVideoResults().size() : 0);
    }
    
    /**
     * 채널별 통합 결과를 Redis Hash 구조로 저장
     */
    private void saveChannelResultToRedis(AgentChannelResult result) {
        // 1. 비디오별 결과 저장
        if (result.getVideoResults() != null) {
            result.getVideoResults().forEach(videoResult -> {
                String videoKey = "video:" + videoResult.getVideoId();
                Map<String, String> videoData = new HashMap<>();
                putIfNotNull(videoData, "summarize", videoResult.getSummary());
                putIfNotNull(videoData, "communication", videoResult.getCommunicationReport());
                putIfNotNull(videoData, "video_title", videoResult.getVideoTitle());
                putIfNotNull(videoData, "summarized_at", videoResult.getSummarizedAt());
                putIfNotNull(videoData, "char_count", videoResult.getCharCount() != null ? String.valueOf(videoResult.getCharCount()) : null);
                putIfNotNull(videoData, "model", videoResult.getModel());
                
                if (!videoData.isEmpty()) {
                    stringRedisTemplate.opsForHash().putAll(videoKey, videoData);
                }
            });
        }
        
        // 2. 채널별 결과 저장
        String channelKey = "channel:" + result.getChannelId();
        Map<String, String> channelData = new HashMap<>();
        putIfNotNull(channelData, "profile", result.getProfileReport());
        putIfNotNull(channelData, "ecosystem", result.getEcosystemReport());
        putIfNotNull(channelData, "profiled_at", result.getProfiledAt());
        putIfNotNull(channelData, "model", result.getModel());
        
        if (!channelData.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(channelKey, channelData);
        }
    }

    // insert filtered comments to DB
    @Override
    @Transactional
    public Integer insertFilteredComment(List<AgentFilteredComment> agentFilteredComments) {
        int savedCount = 0;

        for (AgentFilteredComment comment : agentFilteredComments) {
            // 1. videoId(String) → Integer
            Integer videoId = findVideoIdByYoutubeVideoId(comment.getVideoId());
            
            // 2. save to youtube_comments of DB
            Integer result = agentMapper.insertFilteredComment(
                videoId,
                comment.getCommentId(),
                comment.getTextOriginal(),
                comment.getAuthorName(),
                comment.getPublishedAt(),
                comment.getLikeCount(),
                comment.getAuthorChannelId(),
                comment.getUpdatedAt(),
                comment.getParentId(),
                comment.getTotalReplyCount(),
                comment.getCanRate(),
                comment.getViewerRating()
            );
            
            if (result > 0) {
                savedCount++;
            }
        }
        
        return savedCount;
    }
    
    /**
     * null이 아닐 때만 Map에 추가 (공통 유틸리티)
     */
    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
