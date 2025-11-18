package com.medi.backend.youtube.redis.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import com.medi.backend.youtube.redis.dto.RedisSyncResult;
import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;
import com.medi.backend.youtube.redis.util.YoutubeApiClientUtil;
import com.medi.backend.youtube.service.YoutubeOAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * YouTube 데이터 Redis 동기화 통합 서비스 구현체
 * 
 * 1. get the channel list of the user from YouTube API (independently from DB)
 * 
 * 2. save the top 20 video IDs of each channel to Redis
 *    Key: channel:{channel_id}:top20_video_ids
 *    Type: Set
 *    Value: video ID list
 * 
 * 3. save the video metadata to Redis (after 2 is completed)
 *    Key: video:{video_id}:meta:json
 *    Type: String (JSON)
 *    Value: {channel_id, video_id, video_title, video_tags}
 * 
 * 4. save the video comments to Redis (after 3 is completed)
 *    초기 동기화: Key: video:{video_id}:comments:init (채널 프로파일링용)
 *                Type: String (JSON array)
 *    증분 동기화: Key: video:{video_id}:comments (원본 데이터, 절대 수정 금지)
 *                Type: Hash
 *                Field: comment_id, Value: JSON 문자열 (전체 메타데이터)
 *    필터링 결과: Key: video:{video_id}:classification (FastAPI agent가 저장)
 *                Type: Hash
 *                Field: comment_id, Value: JSON 문자열 (분류 결과)
 * 
 * transaction processing:
 * - @Transactional: ensure that each step is executed sequentially
 * - if one step fails, the previous steps are maintained (partial failure is allowed)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeRedisSyncServiceImpl implements YoutubeRedisSyncService {

    private final YoutubeVideoService videoService;
    private final YoutubeCommentService commentService;
    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeTranscriptService youtubeTranscriptService;
    
    // Redis 템플릿
    private final StringRedisTemplate stringRedisTemplate;
    @Qualifier("queueRedisTemplate")
    private final StringRedisTemplate queueRedisTemplate;
    private final ObjectMapper objectMapper;

    // full sync process (initial sync)
    @Override
    @Transactional
    public RedisSyncResult syncToRedis(Integer userId) {
        try {
            log.info("Redis 동기화 시작: userId={}", userId);
            
            // 1. make a YouTube API Client
            YouTube yt = YoutubeApiClientUtil.buildClientForUser(youtubeOAuthService, userId);
            
            // 2. get the channel list of the user from YouTube API
            List<String> channelIds = YoutubeApiClientUtil.fetchUserChannelIds(yt);
            
            if (channelIds.isEmpty()) {
                log.warn("사용자 {}의 채널이 없습니다", userId);
                return RedisSyncResult.builder()
                    .channelCount(0)
                    .videoCount(0)
                    .commentCount(0)
                    .success(true)
                    .build();
            }

            log.info("YouTube API를 통해 조회된 채널 개수: userId={}, 채널={}개", userId, channelIds.size());

            // 3. save the top 20 video IDs, video metadata of each channel to Redis
            Map<String, List<RedisYoutubeVideo>> videosByChannel = 
                videoService.getTop20VideosByChannel(yt, channelIds);
            
            if (videosByChannel.isEmpty()) {
                log.warn("조회수 상위 20개 영상이 없습니다: userId={}", userId);
                return RedisSyncResult.builder()
                    .channelCount(channelIds.size())
                    .videoCount(0)
                    .commentCount(0)
                    .success(true)
                    .build();
            }

            // calculate the total number of videos
            int totalVideoCount = videosByChannel.values().stream()
                .mapToInt(List::size)
                .sum();

            // 4. save the comments metadata of each video to Redis(limit: 100)
            long totalCommentCount = commentService.syncTop20VideoComments(
                userId, videosByChannel, SyncOptions.initialSync());

            // 5. save the transcripts for channel analysis (채널 성격 파악용 - 프로파일용 상위 20개 영상)
            List<String> allVideoIds = videosByChannel.values().stream()
                .flatMap(List::stream)
                .map(RedisYoutubeVideo::getYoutubeVideoId)
                .collect(Collectors.toList());
            
            if (!allVideoIds.isEmpty()) {
                log.info("초기 동기화: {}개 영상의 자막 저장 시작 (채널 성격 파악용)", allVideoIds.size());
                youtubeTranscriptService.saveTranscriptsToRedis(allVideoIds, yt);
            }

            // 작업 큐에 채널별 작업 추가
            for (Map.Entry<String, List<RedisYoutubeVideo>> entry : videosByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<String> videoIds = entry.getValue().stream()
                    .map(RedisYoutubeVideo::getYoutubeVideoId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());
                
                if (!videoIds.isEmpty()) {
                    enqueueAgentTask(channelId, videoIds);
                }
            }

            log.info("Redis 동기화 완료: userId={}, 채널={}개, 비디오={}개, 댓글={}개", 
                userId, videosByChannel.size(), totalVideoCount, totalCommentCount);

            return RedisSyncResult.builder()
                .channelCount(videosByChannel.size())
                .videoCount(totalVideoCount)
                .commentCount(totalCommentCount)
                .success(true)
                .build();

        } catch (Exception e) {
            log.error("Redis 동기화 실패: userId={}", userId, e);
            return RedisSyncResult.builder()
                .channelCount(0)
                .videoCount(0)
                .commentCount(0)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * 증분 동기화: 새로 추가된 비디오들의 메타데이터와 댓글을 Redis에 저장
     * 
     * 실행 순서:
     * 1. 비디오 메타데이터 조회 및 저장 (전체 메타데이터)
     * 2. 비디오 댓글 조회 및 저장 (전체 댓글, 제한 없음)
     * 
     * API 호출 최소화:
     * - 비디오 메타데이터는 배치로 한 번에 조회 (50개씩)
     * - 댓글은 각 비디오마다 조회하되, 이미 조회한 비디오 정보 재사용
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videoIds 새로 추가된 비디오 ID 리스트
     * @return 동기화 결과 정보
     */
    @Override
    @Transactional
    public RedisSyncResult syncIncrementalToRedis(Integer userId, List<String> videoIds) {
        try {
            log.info("증분 Redis 동기화 시작: userId={}, 비디오 개수={}", userId, videoIds != null ? videoIds.size() : 0);
            
            // 비디오 ID 리스트 검증
            if (videoIds == null || videoIds.isEmpty()) {
                log.warn("비디오 ID 리스트가 비어있습니다: userId={}", userId);
                return RedisSyncResult.builder()
                    .channelCount(0)
                    .videoCount(0)
                    .commentCount(0)
                    .success(false)
                    .errorMessage("비디오 ID 리스트가 비어있습니다")
                    .build();
            }
            
            // 증분 동기화 옵션 (전체 메타데이터 + 전체 댓글)
            SyncOptions incrementalOptions = SyncOptions.incrementalSync();
            
            // 1단계: 비디오 메타데이터 저장 (전체 메타데이터)
            // ⭐ API 호출: 비디오 ID 리스트를 50개씩 묶어서 한 번에 조회
            int savedVideoCount = videoService.syncVideoMetadata(userId, videoIds, incrementalOptions);
            
            if (savedVideoCount == 0) {
                log.warn("비디오 메타데이터 저장 실패: userId={}", userId);
                return RedisSyncResult.builder()
                    .channelCount(0)
                    .videoCount(0)
                    .commentCount(0)
                    .success(false)
                    .errorMessage("비디오 메타데이터 저장 실패")
                    .build();
            }
            
            // 2단계: 비디오 댓글 저장 (전체 댓글, 제한 없음)
            // ⭐ API 호출: 각 비디오마다 댓글 조회 (옵션에 따라 제한 없음)
            long totalCommentCount = commentService.syncVideoComments(userId, videoIds, incrementalOptions);
            
            // Redis에서 video 메타데이터를 조회하여 channelId별로 그룹화
            Map<String, List<String>> videoIdsByChannel = groupVideoIdsByChannel(videoIds);
            
            // 채널별로 작업 큐에 추가
            for (Map.Entry<String, List<String>> entry : videoIdsByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<String> channelVideoIds = entry.getValue();
                
                if (!channelVideoIds.isEmpty()) {
                    enqueueAgentTask(channelId, channelVideoIds);
                }
            }
            
            log.info("증분 Redis 동기화 완료: userId={}, 비디오={}개, 댓글={}개", 
                userId, savedVideoCount, totalCommentCount);
            
            return RedisSyncResult.builder()
                .channelCount(videoIdsByChannel.size())
                .videoCount(savedVideoCount)
                .commentCount(totalCommentCount)
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("증분 Redis 동기화 실패: userId={}", userId, e);
            return RedisSyncResult.builder()
                .channelCount(0)
                .videoCount(0)
                .commentCount(0)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * 에이전트 작업 큐에 작업 추가
     * 
     * Redis 큐 구조:
     * - Key: profiling_agent:tasks:queue
     * - Type: List
     * - Spring 백엔드: LPUSH로 작업 추가 (왼쪽에 추가)
     * - FastAPI Agent: RPOP/BRPOP으로 작업 꺼내기 (오른쪽에서 꺼내기, read + delete 동시 수행)
     * 
     * LPUSH + RPOP 조합 = FIFO (First In First Out)
     * 
     * @param channelId YouTube 채널 ID
     * @param videoIds 처리할 비디오 ID 리스트
     */
    private void enqueueAgentTask(String channelId, List<String> videoIds) {
        try {
            Map<String, Object> task = new HashMap<>();
            task.put("taskId", UUID.randomUUID().toString());
            task.put("channelId", channelId);
            task.put("videoIds", videoIds);
            task.put("createdAt", LocalDateTime.now().toString());
            
            String taskJson = objectMapper.writeValueAsString(task);
            queueRedisTemplate.opsForList().leftPush("profiling_agent:tasks:queue", taskJson);
            
            log.info("에이전트 작업 큐에 추가: channelId={}, videoCount={}", 
                channelId, videoIds.size());
        } catch (Exception e) {
            log.error("에이전트 작업 큐 추가 실패: channelId={}", channelId, e);
            // 큐 추가 실패해도 Redis 동기화는 이미 완료되었으므로 예외를 던지지 않음
        }
    }
    
    /**
     * 비디오 ID 리스트를 channelId별로 그룹화
     * Redis에서 비디오 메타데이터를 조회하여 channelId 추출
     * 
     * @param videoIds 비디오 ID 리스트
     * @return channelId를 키로 하는 비디오 ID 리스트 맵
     */
    private Map<String, List<String>> groupVideoIdsByChannel(List<String> videoIds) {
        Map<String, List<String>> result = new HashMap<>();
        
        for (String videoId : videoIds) {
            try {
                // Redis에서 비디오 메타데이터 조회
                String metaKey = "video:" + videoId + ":meta:json";
                String metaJson = stringRedisTemplate.opsForValue().get(metaKey);
                
                if (metaJson != null) {
                    // JSON 파싱하여 channelId 추출
                    Map<String, Object> meta = objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {});
                    String channelId = (String) meta.get("channel_id");
                    
                    if (channelId != null && !channelId.isBlank()) {
                        result.computeIfAbsent(channelId, k -> new java.util.ArrayList<>()).add(videoId);
                    }
                }
            } catch (Exception e) {
                log.warn("비디오 {}의 channelId 추출 실패", videoId, e);
            }
        }
        
        return result;
    }
}

