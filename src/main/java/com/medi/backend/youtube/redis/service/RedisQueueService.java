package com.medi.backend.youtube.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Queue 관리 서비스
 * 
 * DB 1: Task Queue
 * - profiling_agent:tasks:queue (Profiling 작업)
 * - filtering_agent:tasks:queue (Filtering 작업)
 */
@Slf4j
@Service
public class RedisQueueService {

    private final StringRedisTemplate redisQueueTemplate;
    private final ObjectMapper objectMapper;
    private static final String PROFILING_QUEUE_KEY = "profiling_agent:tasks:queue";
    private static final String FILTERING_QUEUE_KEY = "filtering_agent:tasks:queue";

    public RedisQueueService(
        @Qualifier("redisQueueTemplate") StringRedisTemplate redisQueueTemplate,
        ObjectMapper objectMapper
    ) {
        this.redisQueueTemplate = redisQueueTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Profiling Agent 작업 추가
     */
    public void enqueueProfiling(String channelId, List<String> videoIds) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("type", "profiling");  // ⭐ 명시적으로 "profiling"
            
            if (videoIds != null && !videoIds.isEmpty()) {
                task.put("videoIds", videoIds);
            }
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 PROFILING Queue에 추가
            redisQueueTemplate.opsForList().leftPush(PROFILING_QUEUE_KEY, taskJson);
            
            log.info("✅ Profiling task 추가 (DB 1): channelId={}, queue={}, type=profiling", 
                channelId, PROFILING_QUEUE_KEY);
        } catch (Exception e) {
            log.error("❌ Profiling task 추가 실패: channelId={}", channelId, e);
            throw new RuntimeException("Failed to enqueue profiling task", e);
        }
    }

    /**
     * Filtering Agent 작업 추가
     */
    public void enqueueFiltering(String channelId, List<String> videoIds) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("type", "filtering");  // ⭐ 명시적으로 "filtering"
            task.put("videoIds", videoIds);
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 FILTERING Queue에 추가
            redisQueueTemplate.opsForList().leftPush(FILTERING_QUEUE_KEY, taskJson);
            
            log.info("✅ Filtering task 추가 (DB 1): channelId={}, queue={}, type=filtering, videoCount={}", 
                channelId, FILTERING_QUEUE_KEY, videoIds.size());
        } catch (Exception e) {
            log.error("❌ Filtering task 추가 실패: channelId={}", channelId, e);
            throw new RuntimeException("Failed to enqueue filtering task", e);
        }
    }

    /**
     * Queue 길이 확인 (모니터링용)
     */
    public Map<String, Long> getQueueStats() {
        Map<String, Long> stats = new HashMap<>();
        
        Long profilingLength = redisQueueTemplate.opsForList().size(PROFILING_QUEUE_KEY);
        Long filteringLength = redisQueueTemplate.opsForList().size(FILTERING_QUEUE_KEY);
        
        stats.put("profiling_queue_length", profilingLength != null ? profilingLength : 0L);
        stats.put("filtering_queue_length", filteringLength != null ? filteringLength : 0L);
        
        log.debug("Queue 통계: Profiling={}, Filtering={}", 
            stats.get("profiling_queue_length"), 
            stats.get("filtering_queue_length"));
        
        return stats;
    }
    
    /**
     * Queue 비우기 (디버깅용)
     */
    public void clearQueue(String queueType) {
        try {
            if ("profiling".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(PROFILING_QUEUE_KEY);
                log.info("🗑️ Profiling Queue 비움");
            } else if ("filtering".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(FILTERING_QUEUE_KEY);
                log.info("🗑️ Filtering Queue 비움");
            } else if ("all".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(PROFILING_QUEUE_KEY);
                redisQueueTemplate.delete(FILTERING_QUEUE_KEY);
                log.info("🗑️ 모든 Queue 비움");
            }
        } catch (Exception e) {
            log.error("❌ Queue 삭제 실패: type={}", queueType, e);
        }
    }
}

