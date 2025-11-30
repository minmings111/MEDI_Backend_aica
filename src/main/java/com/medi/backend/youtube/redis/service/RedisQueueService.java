package com.medi.backend.youtube.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.filter.service.FilterPreferenceService;
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
 * - report_agent:tasks:queue (보고서 작업 통합: legal_report, content_report, threat_analysis)
 * - threat_analysis_agent:tasks:queue (채널 위협 분석 보고서 작업)
 * 
 * DB 0: Form 데이터 저장
 * - channel:{channelId}:form (채널별 Form 데이터, agent에서 프롬프트로 사용)
 */
@Slf4j
@Service
public class RedisQueueService {

    private final StringRedisTemplate redisQueueTemplate;
    private final ObjectMapper objectMapper;
    private final FilterPreferenceService filterPreferenceService;
    private static final String PROFILING_QUEUE_KEY = "profiling_agent:tasks:queue";
    private static final String FILTERING_QUEUE_KEY = "filtering_agent:tasks:queue";
    private static final String REPORT_QUEUE_KEY = "report_agent:tasks:queue";  // 통합된 보고서 큐
    private static final String THREAT_ANALYSIS_QUEUE_KEY = "threat_analysis_agent:tasks:queue";  // 위협 분석 큐
    
    // Redis 저장용 템플릿 (DB 0, 기본 Redis)
    private final StringRedisTemplate stringRedisTemplate;

    public RedisQueueService(
        @Qualifier("redisQueueTemplate") StringRedisTemplate redisQueueTemplate,
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        FilterPreferenceService filterPreferenceService
    ) {
        this.redisQueueTemplate = redisQueueTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.filterPreferenceService = filterPreferenceService;
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
     * - DB 1의 filtering_agent:tasks:queue에 작업 추가
     * - 프롬프트는 큐에 포함하지 않음 (에이전트가 작업 처리 시 Redis에서 직접 읽음)
     * - Redis 키: channel:{channelId}:form (DB 0)
     */
    public void enqueueFiltering(String channelId, List<String> videoIds) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("type", "filtering");  // ⭐ 명시적으로 "filtering"
            task.put("videoIds", videoIds);
            
            // ✅ 프롬프트는 큐에 포함하지 않음
            //    에이전트가 작업 처리 시 Redis(DB 0)에서 직접 읽음
            //    Redis 키: channel:{channelId}:form
            //    - 입력 폼 저장 시 Redis에 저장됨 (TTL 없음 - 영구 저장)
            //    - 에이전트가 없으면 기본 프롬프트 사용
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 FILTERING Queue에 추가
            redisQueueTemplate.opsForList().leftPush(FILTERING_QUEUE_KEY, taskJson);
            
            log.info("✅ Filtering task 추가 (DB 1): channelId={}, queue={}, type=filtering, videoCount={}", 
                channelId, FILTERING_QUEUE_KEY, videoIds.size());
            log.debug("💡 에이전트는 Redis(DB 0)에서 channel:{}:form 키로 프롬프트를 읽어야 합니다.", channelId);
        } catch (Exception e) {
            log.error("❌ Filtering task 추가 실패: channelId={}", channelId, e);
            throw new RuntimeException("Failed to enqueue filtering task", e);
        }
    }

    /**
     * 보고서 작업 추가 (통합 큐)
     * - legal_report, content_report를 하나의 큐로 통합
     * - type 필드로 구분: "legal_report", "content_report"
     * - DB 작업 없이 큐에만 추가
     * - channelId와 userId를 포함하여 사용자 식별 가능
     */
    public void enqueueReport(String channelId, Integer userId, String reportType, Map<String, Object> requestData) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("userId", userId);
            task.put("type", reportType);  // "legal_report" 또는 "content_report"
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 통합 REPORT Queue에 추가
            redisQueueTemplate.opsForList().leftPush(REPORT_QUEUE_KEY, taskJson);
            
            log.info("✅ Report task 추가 (DB 1): channelId={}, userId={}, queue={}, type={}", 
                channelId, userId, REPORT_QUEUE_KEY, reportType);
        } catch (Exception e) {
            log.error("❌ Report task 추가 실패: channelId={}, userId={}, type={}", channelId, userId, reportType, e);
            throw new RuntimeException("Failed to enqueue report task", e);
        }
    }

    /**
     * 합법 보고서 (Legal Report) 작업 추가 (하위 호환성 유지)
     * @deprecated enqueueReport() 사용 권장
     */
    @Deprecated
    public void enqueueLegalReport(String channelId, Integer userId, Map<String, Object> requestData) {
        enqueueReport(channelId, userId, "legal_report", requestData);
    }

    /**
     * 콘텐츠 보고서 (Content Report) 작업 추가 (하위 호환성 유지)
     * @deprecated enqueueReport() 사용 권장
     */
    @Deprecated
    public void enqueueContentReport(String channelId, Integer userId, Map<String, Object> requestData) {
        enqueueReport(channelId, userId, "content_report", requestData);
    }

    /**
     * 채널 위협 분석 보고서 (Threat Analysis Report) 작업 추가
     * - DB 작업 없이 큐에만 추가
     * - channelId와 userId를 포함하여 사용자 식별 가능
     */
    public void enqueueThreatAnalysis(String channelId, Integer userId, Map<String, Object> requestData) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("userId", userId);
            task.put("type", "threat_analysis");
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 THREAT ANALYSIS Queue에 추가
            redisQueueTemplate.opsForList().leftPush(THREAT_ANALYSIS_QUEUE_KEY, taskJson);
            
            log.info("✅ Threat Analysis task 추가 (DB 1): channelId={}, userId={}, queue={}, type=threat_analysis", 
                channelId, userId, THREAT_ANALYSIS_QUEUE_KEY);
        } catch (Exception e) {
            log.error("❌ Threat Analysis task 추가 실패: channelId={}, userId={}", channelId, userId, e);
            throw new RuntimeException("Failed to enqueue threat analysis task", e);
        }
    }

    /**
     * Queue 길이 확인 (모니터링용)
     */
    public Map<String, Long> getQueueStats() {
        Map<String, Long> stats = new HashMap<>();
        
        Long profilingLength = redisQueueTemplate.opsForList().size(PROFILING_QUEUE_KEY);
        Long filteringLength = redisQueueTemplate.opsForList().size(FILTERING_QUEUE_KEY);
        Long reportLength = redisQueueTemplate.opsForList().size(REPORT_QUEUE_KEY);
        Long threatAnalysisLength = redisQueueTemplate.opsForList().size(THREAT_ANALYSIS_QUEUE_KEY);
        
        stats.put("profiling_queue_length", profilingLength != null ? profilingLength : 0L);
        stats.put("filtering_queue_length", filteringLength != null ? filteringLength : 0L);
        stats.put("report_queue_length", reportLength != null ? reportLength : 0L);
        stats.put("threat_analysis_queue_length", threatAnalysisLength != null ? threatAnalysisLength : 0L);
        
        log.debug("Queue 통계: Profiling={}, Filtering={}, Report={}, ThreatAnalysis={}", 
            stats.get("profiling_queue_length"), 
            stats.get("filtering_queue_length"),
            stats.get("report_queue_length"),
            stats.get("threat_analysis_queue_length"));
        
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
            } else if ("report".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(REPORT_QUEUE_KEY);
                log.info("🗑️ Report Queue 비움");
            } else if ("threat_analysis".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(THREAT_ANALYSIS_QUEUE_KEY);
                log.info("🗑️ Threat Analysis Queue 비움");
            } else if ("all".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(PROFILING_QUEUE_KEY);
                redisQueueTemplate.delete(FILTERING_QUEUE_KEY);
                redisQueueTemplate.delete(REPORT_QUEUE_KEY);
                redisQueueTemplate.delete(THREAT_ANALYSIS_QUEUE_KEY);
                log.info("🗑️ 모든 Queue 비움");
            }
        } catch (Exception e) {
            log.error("❌ Queue 삭제 실패: type={}", queueType, e);
        }
    }
}

