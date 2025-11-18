package com.medi.backend.agent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentIndividualResult;
import com.medi.backend.agent.mapper.AgentMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService{

    private final AgentMapper agentMapper;
    private final StringRedisTemplate stringRedisTemplate;
    
    // 채널별로 개별 결과를 수집하는 Map
    // Key: YouTube channel_id (String), Value: ChannelResultCollector
    private final Map<String, ChannelResultCollector> resultCollectors = new ConcurrentHashMap<>();
    
    public AgentServiceImpl(AgentMapper agentMapper, StringRedisTemplate stringRedisTemplate) {
        this.agentMapper = agentMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

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

    @Override
    @Transactional
    public void collectIndividualResult(AgentIndividualResult result) {
        // 1. 비디오 결과인지 채널 결과인지 판단
        if (result.getVideoId() != null) {
            // 비디오 결과 처리
            handleVideoResult(result);
        } else if (result.getChannelId() != null) {
            // 채널 결과 처리
            handleChannelResult(result);
        } else {
            log.warn("Invalid result: both videoId and channelId are null");
            return;
        }
    }
    
    /**
     * 비디오 결과 처리 (summary 또는 communication_report)
     */
    private void handleVideoResult(AgentIndividualResult result) {
        // 1. 채널 ID 찾기 (비디오 → 채널)
        Integer videoId = findVideoIdByYoutubeVideoId(result.getVideoId());
        if (videoId == null) {
            log.warn("Video not found: {}", result.getVideoId());
            return;
        }
        
        // 2. 비디오의 채널 ID 찾기
        Integer channelId = findChannelIdByVideoId(videoId);
        if (channelId == null) {
            log.warn("Channel not found for video: {}", result.getVideoId());
            return;
        }
        
        // 3. 채널의 YouTube ID 찾기
        String youtubeChannelId = findYoutubeChannelIdByChannelId(channelId);
        if (youtubeChannelId == null) {
            log.warn("YouTube channel ID not found for channel: {}", channelId);
            return;
        }
        
        // 4. ChannelResultCollector 조회/생성
        ChannelResultCollector collector = resultCollectors.computeIfAbsent(
            youtubeChannelId, 
            ChannelResultCollector::new
        );
        
        // 5. VideoResultCollector 조회/생성
        VideoResultCollector videoCollector = collector.getVideoResults().computeIfAbsent(
            result.getVideoId(),
            VideoResultCollector::new
        );
        
        // 6. 결과 저장
        if (result.getSummary() != null) {
            videoCollector.setSummary(result.getSummary());
        }
        if (result.getCommunicationReport() != null) {
            videoCollector.setCommunicationReport(result.getCommunicationReport());
        }
        
        // 7. 완료 여부 확인 및 저장
        if (collector.isComplete()) {
            saveCollectedResults(youtubeChannelId);
            // 저장 후 수집 데이터 제거
            resultCollectors.remove(youtubeChannelId);
        }
    }
    
    /**
     * 채널 결과 처리 (profile_report 또는 ecosystem_report)
     */
    private void handleChannelResult(AgentIndividualResult result) {
        // 1. ChannelResultCollector 조회/생성
        ChannelResultCollector collector = resultCollectors.computeIfAbsent(
            result.getChannelId(),
            ChannelResultCollector::new
        );
        
        // 2. 결과 저장
        if (result.getProfileReport() != null) {
            collector.setProfileReport(result.getProfileReport());
        }
        if (result.getEcosystemReport() != null) {
            collector.setEcosystemReport(result.getEcosystemReport());
        }
        
        // 3. 완료 여부 확인 및 저장
        if (collector.isComplete()) {
            saveCollectedResults(result.getChannelId());
            // 저장 후 수집 데이터 제거
            resultCollectors.remove(result.getChannelId());
        }
    }
    
    /**
     * 수집된 결과를 Redis와 SQL에 저장
     */
    private void saveCollectedResults(String youtubeChannelId) {
        ChannelResultCollector collector = resultCollectors.get(youtubeChannelId);
        if (collector == null) {
            log.warn("Collector not found for channel: {}", youtubeChannelId);
            return;
        }
        
        // 1. YouTube channel_id → 내부 channel_id 변환
        Integer channelId = findChannelIdByYoutubeChannelId(youtubeChannelId);
        if (channelId == null) {
            log.warn("Channel not found: {}", youtubeChannelId);
            return;
        }
        
        // 2. Redis 저장
        saveToRedis(youtubeChannelId, collector);
        
        // 3. SQL 저장
        // 3-1. 비디오별 결과 저장
        for (VideoResultCollector videoResult : collector.getVideoResults().values()) {
            Integer videoId = findVideoIdByYoutubeVideoId(videoResult.getYoutubeVideoId());
            if (videoId == null) {
                log.warn("Video not found: {}", videoResult.getYoutubeVideoId());
                continue;
            }
            
            agentMapper.insertResultVideo(
                videoId,
                videoResult.getSummary(),
                videoResult.getCommunicationReport()
            );
        }
        
        // 3-2. 채널별 결과 저장
        agentMapper.insertResultChannel(
            channelId,
            collector.getProfileReport(),
            collector.getEcosystemReport()
        );
        
        log.info("Saved analysis results for channel: {}", youtubeChannelId);
    }
    
    /**
     * Redis Hash 구조로 저장
     */
    private void saveToRedis(String youtubeChannelId, ChannelResultCollector collector) {
        // 1. 비디오별 결과 저장
        for (VideoResultCollector videoResult : collector.getVideoResults().values()) {
            String videoKey = "video:" + videoResult.getYoutubeVideoId();
            if (videoResult.getSummary() != null) {
                stringRedisTemplate.opsForHash().put(videoKey, "summarize", videoResult.getSummary());
            }
            if (videoResult.getCommunicationReport() != null) {
                stringRedisTemplate.opsForHash().put(videoKey, "communication", videoResult.getCommunicationReport());
            }
        }
        
        // 2. 채널별 결과 저장
        String channelKey = "channel:" + youtubeChannelId;
        if (collector.getProfileReport() != null) {
            stringRedisTemplate.opsForHash().put(channelKey, "profile", collector.getProfileReport());
        }
        if (collector.getEcosystemReport() != null) {
            stringRedisTemplate.opsForHash().put(channelKey, "ecosystem", collector.getEcosystemReport());
        }
    }
    
    /**
     * 비디오 ID로 채널 ID 찾기
     */
    private Integer findChannelIdByVideoId(Integer videoId) {
        return agentMapper.findChannelIdByVideoId(videoId);
    }
    
    /**
     * 채널 ID로 YouTube 채널 ID 찾기
     */
    private String findYoutubeChannelIdByChannelId(Integer channelId) {
        return agentMapper.findYoutubeChannelIdByChannelId(channelId);
    }
    
    /**
     * 개별 결과 수집용 내부 클래스 - 채널별
     */
    private static class ChannelResultCollector {
        private final String youtubeChannelId;
        private final Map<String, VideoResultCollector> videoResults = new HashMap<>();
        private String profileReport;
        private String ecosystemReport;
        
        public ChannelResultCollector(String youtubeChannelId) {
            this.youtubeChannelId = youtubeChannelId;
        }
        
        public boolean isComplete() {
            // 1. 모든 비디오 결과가 완료되었는지 확인
            for (VideoResultCollector video : videoResults.values()) {
                if (!video.isComplete()) {
                    return false;
                }
            }
            
            // 2. 채널별 결과가 완료되었는지 확인
            return profileReport != null && ecosystemReport != null;
        }
        
        // Getters and Setters
        public String getYoutubeChannelId() { return youtubeChannelId; }
        public Map<String, VideoResultCollector> getVideoResults() { return videoResults; }
        public String getProfileReport() { return profileReport; }
        public void setProfileReport(String profileReport) { this.profileReport = profileReport; }
        public String getEcosystemReport() { return ecosystemReport; }
        public void setEcosystemReport(String ecosystemReport) { this.ecosystemReport = ecosystemReport; }
    }
    
    /**
     * 개별 결과 수집용 내부 클래스 - 비디오별
     */
    private static class VideoResultCollector {
        private final String youtubeVideoId;
        private String summary;
        private String communicationReport;
        
        public VideoResultCollector(String youtubeVideoId) {
            this.youtubeVideoId = youtubeVideoId;
        }
        
        public boolean isComplete() {
            return summary != null && communicationReport != null;
        }
        
        // Getters and Setters
        public String getYoutubeVideoId() { return youtubeVideoId; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getCommunicationReport() { return communicationReport; }
        public void setCommunicationReport(String communicationReport) { this.communicationReport = communicationReport; }
    }
}
