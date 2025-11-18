package com.medi.backend.agent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentChannelResult;
import com.medi.backend.agent.dto.AgentSummaryResult;
import com.medi.backend.agent.dto.AgentCommunicationResult;
import com.medi.backend.agent.dto.AgentProfileResult;
import com.medi.backend.agent.dto.AgentEcosystemResult;
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

    // 채널별로 개별 결과를 수집하는 Map
    // Key: YouTube channel_id (String), Value: ChannelResultCollector
    private final Map<String, ChannelResultCollector> resultCollectors = new ConcurrentHashMap<>();


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

    
    // collect results to DB and Redis
    @Override
    @Transactional
    public void collectSummaryResult(AgentSummaryResult result) {
        handleSummaryResult(result);
    }
    
    @Override
    @Transactional
    public void collectCommunicationResult(AgentCommunicationResult result) {
        handleCommunicationResult(result);
    }
    
    @Override
    @Transactional
    public void collectProfileResult(AgentProfileResult result) {
        handleProfileResult(result);
    }
    
    @Override
    @Transactional
    public void collectEcosystemResult(AgentEcosystemResult result) {
        handleEcosystemResult(result);
    }

    
    /**
     * summary 결과 처리
     */
    private void handleSummaryResult(AgentSummaryResult result) {
        processVideoResult(result.getVideoId(), videoCollector -> {
            videoCollector.setSummary(result.getSummary());
            setIfNotNull(videoCollector::setVideoTitle, result.getVideoTitle());
            setIfNotNull(videoCollector::setSummarizedAt, result.getSummarizedAt());
            setIfNotNull(videoCollector::setCharCount, result.getCharCount());
            setIfNotNull(videoCollector::setModel, result.getModel());
        });
    }
    
    /**
     * communication_report 결과 처리
     */
    private void handleCommunicationResult(AgentCommunicationResult result) {
        processVideoResult(result.getVideoId(), videoCollector -> {
            videoCollector.setCommunicationReport(result.getCommunicationReport());
            setIfNotNullIfAbsent(videoCollector::setModel, result.getModel(), videoCollector.getModel());
        });
    }
    
    /**
     * 비디오 결과 처리 공통 로직
     */
    private void processVideoResult(String youtubeVideoId, java.util.function.Consumer<VideoResultCollector> processor) {
        String youtubeChannelId = findYoutubeChannelIdByVideoId(youtubeVideoId);
        if (youtubeChannelId == null) {
            return;
        }
        
        VideoResultCollector videoCollector = getOrCreateVideoCollector(youtubeChannelId, youtubeVideoId);
        processor.accept(videoCollector);
        checkAndSaveIfComplete(youtubeChannelId);
    }
    
    /**
     * profile_report 결과 처리
     */
    private void handleProfileResult(AgentProfileResult result) {
        processChannelResult(result.getChannelId(), collector -> {
            collector.setProfileReport(result.getProfileReport());
            setIfNotNull(collector::setProfiledAt, result.getProfiledAt());
            setIfNotNull(collector::setModel, result.getModel());
        });
    }
    
    /**
     * ecosystem_report 결과 처리
     */
    private void handleEcosystemResult(AgentEcosystemResult result) {
        processChannelResult(result.getChannelId(), collector -> {
            collector.setEcosystemReport(result.getEcosystemReport());
            setIfNotNullIfAbsent(collector::setProfiledAt, result.getProfiledAt(), collector.getProfiledAt());
            setIfNotNullIfAbsent(collector::setModel, result.getModel(), collector.getModel());
        });
    }
    
    /**
     * 채널 결과 처리 공통 로직
     */
    private void processChannelResult(String youtubeChannelId, java.util.function.Consumer<ChannelResultCollector> processor) {
        ChannelResultCollector collector = getOrCreateChannelCollector(youtubeChannelId);
        processor.accept(collector);
        checkAndSaveIfComplete(youtubeChannelId);
    }
    
    /**
     * 비디오 ID로 YouTube 채널 ID 찾기 (공통 로직)
     */
    private String findYoutubeChannelIdByVideoId(String youtubeVideoId) {
        Integer videoId = findVideoIdByYoutubeVideoId(youtubeVideoId);
        if (videoId == null) {
            log.warn("Video not found: {}", youtubeVideoId);
            return null;
        }
        
        Integer channelId = findChannelIdByVideoId(videoId);
        if (channelId == null) {
            log.warn("Channel not found for video: {}", youtubeVideoId);
            return null;
        }
        
        String youtubeChannelId = findYoutubeChannelIdByChannelId(channelId);
        if (youtubeChannelId == null) {
            log.warn("YouTube channel ID not found for channel: {}", channelId);
            return null;
        }
        
        return youtubeChannelId;
    }
    
    /**
     * ChannelResultCollector 조회/생성 (공통 로직)
     */
    private ChannelResultCollector getOrCreateChannelCollector(String youtubeChannelId) {
        return resultCollectors.computeIfAbsent(youtubeChannelId, ChannelResultCollector::new);
    }
    
    /**
     * VideoResultCollector 조회/생성 (공통 로직)
     */
    private VideoResultCollector getOrCreateVideoCollector(String youtubeChannelId, String youtubeVideoId) {
        ChannelResultCollector collector = getOrCreateChannelCollector(youtubeChannelId);
        return collector.getVideoResults().computeIfAbsent(youtubeVideoId, VideoResultCollector::new);
    }
    
    /**
     * 완료 여부 확인 및 저장 (공통 로직)
     */
    private void checkAndSaveIfComplete(String youtubeChannelId) {
        ChannelResultCollector collector = resultCollectors.get(youtubeChannelId);
        if (collector != null && collector.isComplete()) {
            saveCollectedResults(youtubeChannelId);
            resultCollectors.remove(youtubeChannelId);
        }
    }
    
    /**
     * null이 아닐 때만 setter 호출 (공통 유틸리티)
     */
    private <T> void setIfNotNull(java.util.function.Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
    
    /**
     * null이 아니고 기존 값이 null일 때만 setter 호출 (공통 유틸리티)
     */
    private <T> void setIfNotNullIfAbsent(java.util.function.Consumer<T> setter, T value, T currentValue) {
        if (value != null && currentValue == null) {
            setter.accept(value);
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
                videoResult.getCommunicationReport(),
                videoResult.getVideoTitle(),
                videoResult.getSummarizedAt(),
                videoResult.getCharCount(),
                videoResult.getModel()
            );
        }
        
        // 3-2. 채널별 결과 저장
        agentMapper.insertResultChannel(
            channelId,
            collector.getProfileReport(),
            collector.getEcosystemReport(),
            collector.getProfiledAt(),
            collector.getModel()
        );
        
        log.info("Saved analysis results for channel: {}", youtubeChannelId);
    }
    
    /**
     * Redis Hash 구조로 저장
     */
    private void saveToRedis(String youtubeChannelId, ChannelResultCollector collector) {
        // 1. 비디오별 결과 저장
        collector.getVideoResults().values().forEach(videoResult -> {
            String videoKey = "video:" + videoResult.getYoutubeVideoId();
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
        
        // 2. 채널별 결과 저장
        String channelKey = "channel:" + youtubeChannelId;
        Map<String, String> channelData = new HashMap<>();
        putIfNotNull(channelData, "profile", collector.getProfileReport());
        putIfNotNull(channelData, "ecosystem", collector.getEcosystemReport());
        putIfNotNull(channelData, "profiled_at", collector.getProfiledAt());
        putIfNotNull(channelData, "model", collector.getModel());
        
        if (!channelData.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(channelKey, channelData);
        }
    }
    
    /**
     * null이 아닐 때만 Map에 추가 (공통 유틸리티)
     */
    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
    
    /**
     * 개별 결과 수집용 내부 클래스 - 채널별
     */
    private static class ChannelResultCollector {
        private final String youtubeChannelId;
        private final Map<String, VideoResultCollector> videoResults = new HashMap<>();
        private String profileReport;
        private String ecosystemReport;
        // 메타데이터 필드
        private String profiledAt;
        private String model;
        
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
        public String getProfiledAt() { return profiledAt; }
        public void setProfiledAt(String profiledAt) { this.profiledAt = profiledAt; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
    
    /**
     * 개별 결과 수집용 내부 클래스 - 비디오별
     */
    private static class VideoResultCollector {
        private final String youtubeVideoId;
        private String summary;
        private String communicationReport;
        // 메타데이터 필드
        private String videoTitle;
        private String summarizedAt;
        private Integer charCount;
        private String model;
        
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
        public String getVideoTitle() { return videoTitle; }
        public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }
        public String getSummarizedAt() { return summarizedAt; }
        public void setSummarizedAt(String summarizedAt) { this.summarizedAt = summarizedAt; }
        public Integer getCharCount() { return charCount; }
        public void setCharCount(Integer charCount) { this.charCount = charCount; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

}
