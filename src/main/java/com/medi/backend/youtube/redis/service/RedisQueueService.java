package com.medi.backend.youtube.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.filter.service.FilterPreferenceService;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
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
 * - legal_report_agent:tasks:queue (합법 보고서 작업)
 * - content_report_agent:tasks:queue (콘텐츠 보고서 작업)
 * - form_agent:tasks:queue (입력폼 양식 작업)
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
    private final YoutubeChannelMapper youtubeChannelMapper;
    private static final String PROFILING_QUEUE_KEY = "profiling_agent:tasks:queue";
    private static final String FILTERING_QUEUE_KEY = "filtering_agent:tasks:queue";
    private static final String LEGAL_REPORT_QUEUE_KEY = "legal_report_agent:tasks:queue";
    private static final String CONTENT_REPORT_QUEUE_KEY = "content_report_agent:tasks:queue";
    private static final String FORM_QUEUE_KEY = "form_agent:tasks:queue";
    
    // Redis 저장용 템플릿 (DB 0, 기본 Redis)
    private final StringRedisTemplate stringRedisTemplate;

    public RedisQueueService(
        @Qualifier("redisQueueTemplate") StringRedisTemplate redisQueueTemplate,
        StringRedisTemplate stringRedisTemplate,
        ObjectMapper objectMapper,
        FilterPreferenceService filterPreferenceService,
        YoutubeChannelMapper youtubeChannelMapper
    ) {
        this.redisQueueTemplate = redisQueueTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.filterPreferenceService = filterPreferenceService;
        this.youtubeChannelMapper = youtubeChannelMapper;
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
     * - 프롬프트 정책 블록 포함 (user_policy_block)
     */
    public void enqueueFiltering(String channelId, List<String> videoIds) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("type", "filtering");  // ⭐ 명시적으로 "filtering"
            task.put("videoIds", videoIds);
            
            // ✅ 프롬프트 정책 블록 추가
            try {
                // channelId로 채널 정보 조회 (userId 추출)
                YoutubeChannelDto channel = youtubeChannelMapper.findByYoutubeChannelId(channelId);
                if (channel != null && channel.getUserId() != null) {
                    // 채널별 설정 우선, 없으면 전역 설정
                    Integer channelDbId = channel.getId();
                    String policyBlock = filterPreferenceService.buildPromptPolicyBlock(
                        channel.getUserId(), channelDbId
                    );
                    
                    // 전역 설정 조회 (채널별 설정이 없을 경우)
                    if (policyBlock == null || policyBlock.isEmpty()) {
                        policyBlock = filterPreferenceService.buildPromptPolicyBlock(
                            channel.getUserId(), null
                        );
                    }
                    
                    if (policyBlock != null && !policyBlock.isEmpty()) {
                        task.put("user_policy_block", policyBlock);
                        log.debug("✅ 프롬프트 정책 블록 포함: channelId={}, userId={}, length={}자", 
                            channelId, channel.getUserId(), policyBlock.length());
                    } else {
                        log.debug("⚠️ 프롬프트 정책 블록 없음: channelId={}, userId={}", 
                            channelId, channel.getUserId());
                    }
                } else {
                    log.warn("⚠️ 채널 정보 조회 실패: channelId={}", channelId);
                }
            } catch (Exception e) {
                log.warn("⚠️ 프롬프트 정책 블록 생성 실패 (작업 큐 추가는 계속 진행): channelId={}, error={}", 
                    channelId, e.getMessage());
                // 프롬프트 생성 실패해도 작업 큐 추가는 계속 진행
            }
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 FILTERING Queue에 추가
            redisQueueTemplate.opsForList().leftPush(FILTERING_QUEUE_KEY, taskJson);
            
            log.info("✅ Filtering task 추가 (DB 1): channelId={}, queue={}, type=filtering, videoCount={}, hasPolicy={}", 
                channelId, FILTERING_QUEUE_KEY, videoIds.size(), task.containsKey("user_policy_block"));
        } catch (Exception e) {
            log.error("❌ Filtering task 추가 실패: channelId={}", channelId, e);
            throw new RuntimeException("Failed to enqueue filtering task", e);
        }
    }

    /**
     * 합법 보고서 (Legal Report) 작업 추가
     * - DB 작업 없이 큐에만 추가
     * - channelId와 userId를 포함하여 사용자 식별 가능
     */
    public void enqueueLegalReport(String channelId, Integer userId, Map<String, Object> requestData) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("userId", userId);  // ⭐ 사용자 식별을 위한 userId 추가
            task.put("type", "legal_report");
            
            // ⭐ 프론트에서 전달받은 추가 데이터는 포함하지 않음 (필요한 필드만 task에 추가)
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 LEGAL REPORT Queue에 추가
            redisQueueTemplate.opsForList().leftPush(LEGAL_REPORT_QUEUE_KEY, taskJson);
            
            log.info("✅ Legal Report task 추가 (DB 1): channelId={}, userId={}, queue={}, type=legal_report", 
                channelId, userId, LEGAL_REPORT_QUEUE_KEY);
        } catch (Exception e) {
            log.error("❌ Legal Report task 추가 실패: channelId={}, userId={}", channelId, userId, e);
            throw new RuntimeException("Failed to enqueue legal report task", e);
        }
    }

    /**
     * 콘텐츠 보고서 (Content Report) 작업 추가
     * - DB 작업 없이 큐에만 추가
     * - channelId와 userId를 포함하여 사용자 식별 가능
     */
    public void enqueueContentReport(String channelId, Integer userId, Map<String, Object> requestData) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("userId", userId);  // ⭐ 사용자 식별을 위한 userId 추가
            task.put("type", "content_report");
            
            // ⭐ 프론트에서 전달받은 추가 데이터는 포함하지 않음 (필요한 필드만 task에 추가)
            
            String taskJson = objectMapper.writeValueAsString(task);
            
            // ⭐ DB 1의 CONTENT REPORT Queue에 추가
            redisQueueTemplate.opsForList().leftPush(CONTENT_REPORT_QUEUE_KEY, taskJson);
            
            log.info("✅ Content Report task 추가 (DB 1): channelId={}, userId={}, queue={}, type=content_report", 
                channelId, userId, CONTENT_REPORT_QUEUE_KEY);
        } catch (Exception e) {
            log.error("❌ Content Report task 추가 실패: channelId={}, userId={}", channelId, userId, e);
            throw new RuntimeException("Failed to enqueue content report task", e);
        }
    }

    /**
     * 입력폼 양식 (Form) 작업 추가 및 Redis 저장
     * - 작업 큐(DB 1)에 추가
     * - Redis(DB 0)에 채널별로 저장 (agent에서 프롬프트로 사용)
     */
    public void enqueueAndSaveForm(String channelId, Integer userId, Map<String, Object> formData) {
        try {
            // 1. 작업 큐에 추가 (DB 1)
            Map<String, Object> task = new HashMap<>();
            task.put("channelId", channelId);
            task.put("userId", userId);
            task.put("type", "form");
            
            String taskJson = objectMapper.writeValueAsString(task);
            redisQueueTemplate.opsForList().leftPush(FORM_QUEUE_KEY, taskJson);
            
            log.info("✅ Form task 추가 (DB 1): channelId={}, userId={}, queue={}, type=form", 
                channelId, userId, FORM_QUEUE_KEY);
            
            // 2. Redis에 Form 데이터 저장 (DB 0) - 채널별로 저장
            // 키 패턴: channel:{channelId}:form
            String formRedisKey = "channel:" + channelId + ":form";
            String formDataJson = objectMapper.writeValueAsString(formData);
            stringRedisTemplate.opsForValue().set(formRedisKey, formDataJson);
            
            log.info("✅ Form 데이터 Redis 저장 (DB 0): channelId={}, key={}, dataSize={}자", 
                channelId, formRedisKey, formDataJson.length());
            
        } catch (Exception e) {
            log.error("❌ Form task 추가 및 저장 실패: channelId={}, userId={}", channelId, userId, e);
            throw new RuntimeException("Failed to enqueue and save form task", e);
        }
    }

    /**
     * Queue 길이 확인 (모니터링용)
     */
    public Map<String, Long> getQueueStats() {
        Map<String, Long> stats = new HashMap<>();
        
        Long profilingLength = redisQueueTemplate.opsForList().size(PROFILING_QUEUE_KEY);
        Long filteringLength = redisQueueTemplate.opsForList().size(FILTERING_QUEUE_KEY);
        Long legalReportLength = redisQueueTemplate.opsForList().size(LEGAL_REPORT_QUEUE_KEY);
        Long contentReportLength = redisQueueTemplate.opsForList().size(CONTENT_REPORT_QUEUE_KEY);
        Long formLength = redisQueueTemplate.opsForList().size(FORM_QUEUE_KEY);
        
        stats.put("profiling_queue_length", profilingLength != null ? profilingLength : 0L);
        stats.put("filtering_queue_length", filteringLength != null ? filteringLength : 0L);
        stats.put("legal_report_queue_length", legalReportLength != null ? legalReportLength : 0L);
        stats.put("content_report_queue_length", contentReportLength != null ? contentReportLength : 0L);
        stats.put("form_queue_length", formLength != null ? formLength : 0L);
        
        log.debug("Queue 통계: Profiling={}, Filtering={}, LegalReport={}, ContentReport={}, Form={}", 
            stats.get("profiling_queue_length"), 
            stats.get("filtering_queue_length"),
            stats.get("legal_report_queue_length"),
            stats.get("content_report_queue_length"),
            stats.get("form_queue_length"));
        
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
            } else if ("legal_report".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(LEGAL_REPORT_QUEUE_KEY);
                log.info("🗑️ Legal Report Queue 비움");
            } else if ("content_report".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(CONTENT_REPORT_QUEUE_KEY);
                log.info("🗑️ Content Report Queue 비움");
            } else if ("form".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(FORM_QUEUE_KEY);
                log.info("🗑️ Form Queue 비움");
            } else if ("all".equalsIgnoreCase(queueType)) {
                redisQueueTemplate.delete(PROFILING_QUEUE_KEY);
                redisQueueTemplate.delete(FILTERING_QUEUE_KEY);
                redisQueueTemplate.delete(LEGAL_REPORT_QUEUE_KEY);
                redisQueueTemplate.delete(CONTENT_REPORT_QUEUE_KEY);
                redisQueueTemplate.delete(FORM_QUEUE_KEY);
                log.info("🗑️ 모든 Queue 비움");
            }
        } catch (Exception e) {
            log.error("❌ Queue 삭제 실패: type={}", queueType, e);
        }
    }
}

