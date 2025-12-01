package com.medi.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * 
 * ============================================
 * Redis 저장 항목 분류 (자동/수동 & DB 0/DB 1)
 * ============================================
 * 
 * 【 DB 0 (stringRedisTemplate) - YouTube 데이터 & 캐시 】
 * 
 * ✅ 자동 저장:
 * 
 * 1. 필터 프롬프트 데이터
 * - 키: channel:{youtubeChannelId}:form
 * - 키: user:{userId}:form:global
 * - 타입: String (JSON)
 * - TTL: 없음 (영구 저장)
 * - 트리거: FilterPreferenceServiceImpl.savePreference() (필터 설정 저장 시 자동)
 * - 설명: 사용자 필터링 설정을 프롬프트 형태로 저장 (Agent가 사용)
 * 
 * 2. 비디오 메타데이터
 * - 키: video:{video_id}:meta:json
 * - 타입: String (JSON)
 * - TTL: 3일
 * - 트리거: YoutubeVideoServiceImpl (비디오 동기화 시)
 * - 설명: 비디오 제목, 조회수, 좋아요 수 등 메타데이터
 * 
 * 3. 채널별 Top 10 비디오 ID Set
 * - 키: channel:{channel_id}:top10_video_ids
 * - 타입: Set
 * - TTL: 3일
 * - 트리거: YoutubeVideoServiceImpl (비디오 동기화 시)
 * - 설명: 조회수 상위 10개 비디오 ID
 * 
 * 4. 비디오 댓글 (초기 동기화)
 * - 키: video:{video_id}:comments:init
 * - 타입: String (JSON Array)
 * - TTL: 없음 (수동 삭제)
 * - 트리거: YoutubeRedisSyncService (초기 동기화 시)
 * - 설명: 채널 프로파일링용 초기 댓글 데이터
 * 
 * 5. 비디오 댓글 (증분 동기화)
 * - 키: video:{video_id}:comments
 * - 타입: Hash (Field: comment_id, Value: JSON)
 * - TTL: 없음 (수동 삭제)
 * - 트리거: YoutubeCommentServiceImpl (증분 동기화 시)
 * - 설명: 원본 댓글 데이터 (절대 수정 금지)
 * 
 * 6. 댓글 동기화 커서
 * - 키: video:{video_id}:comment_sync_cursor
 * - 타입: String
 * - TTL: 없음 (수동 삭제)
 * - 트리거: YoutubeCommentServiceImpl (댓글 동기화 시)
 * - 설명: 마지막 동기화 시간 기록
 * 
 * 7. 비디오 자막
 * - 키: video:{video_id}:transcript:json
 * - 타입: String (JSON)
 * - TTL: 3일
 * - 트리거: YoutubeTranscriptServiceImpl (자막 추출 시)
 * - 설명: 비디오 자막 텍스트 (채널 성격 파악용)
 * 
 * ❌ 수동 저장 (API 호출 필요):
 * 
 * 8. 에이전트용 프롬프트 재저장
 * - 키: channel:{youtubeChannelId}:form
 * - 타입: String (JSON)
 * - TTL: 없음 (영구 저장)
 * - 트리거: GET /api/filter/prompt/{channelId} (에이전트가 Redis에서 프롬프트를 못 찾았을 때 호출)
 * - 설명: 에이전트가 Redis에 프롬프트가 없으면 DB에서 조회하여 Redis에 재저장 후 반환
 * (에이전트는 내부 서비스이므로 인증 불필요)
 * 
 * 
 * 【 DB 1 (redisQueueTemplate) - 작업 큐 】
 * 
 * ✅ 자동 저장:
 * 
 * 1. Profiling 작업 큐
 * - 키: profiling_agent:tasks:queue
 * - 타입: List (FIFO)
 * - 트리거: RedisQueueService.enqueueProfiling() (채널 프로파일링 요청 시)
 * - 설명: 채널 프로파일링 작업을 큐에 추가
 * 
 * 2. Filtering 작업 큐
 * - 키: filtering_agent:tasks:queue
 * - 타입: List (FIFO)
 * - 트리거: RedisQueueService.enqueueFiltering() (필터링 작업 요청 시)
 * - 설명: 댓글 필터링 작업을 큐에 추가
 * 
 * ❌ 수동 저장 (API 호출 필요):
 * 
 * 3. Form 작업 큐
 * - 키: form_agent:tasks:queue
 * - 타입: List (FIFO)
 * - 트리거: RedisQueueService.enqueueAndSaveForm() (직접 호출 필요)
 * - 설명: 입력폼 양식 처리 작업을 큐에 추가 (ReportController.createForm()에서 사용)
 * 
 * 4. Legal Report 작업 큐
 * - 키: legal_report_agent:tasks:queue
 * - 타입: List (FIFO)
 * - 트리거: ReportController.createLegalReport() (합법 보고서 생성 요청 시)
 * - 설명: 합법 보고서 생성 작업을 큐에 추가
 * 
 * 5. Content Report 작업 큐
 * - 키: content_report_agent:tasks:queue
 * - 타입: List (FIFO)
 * - 트리거: ReportController.createContentReport() (콘텐츠 보고서 생성 요청 시)
 * - 설명: 콘텐츠 보고서 생성 작업을 큐에 추가
 * 
 * 
 * ============================================
 * Redis 템플릿 사용 정리
 * ============================================
 * 
 * - stringRedisTemplate (Primary): DB 0 사용, YouTube 데이터 및 캐시 저장
 * - redisTemplate: DB 0 사용, JSON 직렬화 (현재 미사용)
 * - redisQueueTemplate: DB 1 사용, 작업 큐 전용
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * DB 0 전용 Redis 템플릿 (Primary)
     * - YouTube 데이터 저장
     * - 필터 프롬프트 캐시
     */
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf, ObjectMapper om) {
        RedisTemplate<String, Object> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        var keySer = new StringRedisSerializer();
        var valSer = new Jackson2JsonRedisSerializer<>(om, Object.class);
        t.setKeySerializer(keySer);
        t.setValueSerializer(valSer);
        t.setHashKeySerializer(keySer);
        t.setHashValueSerializer(valSer);
        t.afterPropertiesSet();
        return t;
    }

    /**
     * 작업 큐 전용 Redis 템플릿 (DB 1)
     * - Profiling, Filtering, Form, Legal Report, Content Report 작업 큐
     */
    @Bean(name = "redisQueueTemplate")
    public StringRedisTemplate redisQueueTemplate() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(1);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 기존 호환성을 위한 별칭 (queueRedisTemplate)
     */
    @Bean(name = "queueRedisTemplate")
    public StringRedisTemplate queueRedisTemplate() {
        return redisQueueTemplate();
    }
}
