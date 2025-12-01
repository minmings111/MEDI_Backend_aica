package com.medi.backend.youtube.redis.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.youtube.YouTube;
import com.medi.backend.youtube.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
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
 * 2. save the top 10 video IDs of each channel to Redis
 * Key: channel:{channel_id}:top10_video_ids
 * Type: Set
 * Value: video ID list
 * 
 * 3. save the video metadata to Redis (after 2 is completed)
 * Key: video:{video_id}:meta:json
 * Type: String (JSON)
 * Value: {channel_id, video_id, video_title, video_tags}
 * 
 * 4. save the video comments to Redis (after 3 is completed)
 * 초기 동기화: Key: video:{video_id}:comments:init (채널 프로파일링용)
 * Type: String (JSON array)
 * 증분 동기화: Key: video:{video_id}:comments (원본 데이터, 절대 수정 금지)
 * Type: Hash
 * Field: comment_id, Value: JSON 문자열 (전체 메타데이터)
 * 필터링 결과: Key: video:{video_id}:classification (FastAPI agent가 저장)
 * Type: Hash
 * Field: comment_id, Value: JSON 문자열 (분류 결과)
 * 
 * transaction processing:
 * - @Transactional: ensure that each step is executed sequentially
 * - if one step fails, the previous steps are maintained (partial failure is
 * allowed)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeRedisSyncServiceImpl implements YoutubeRedisSyncService {

    private final YoutubeVideoService videoService;
    private final YoutubeCommentService commentService;
    private final YoutubeOAuthService youtubeOAuthService;
    private final YoutubeTranscriptService youtubeTranscriptService;
    private final YoutubeVideoMapper youtubeVideoMapper;
    private final YoutubeChannelMapper youtubeChannelMapper;
    private final RedisQueueService redisQueueService;

    // Redis 템플릿
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 동시 실행 제한: 동일 userId의 중복 실행 방지
    private final Set<Integer> syncInProgress = ConcurrentHashMap.newKeySet();

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

            // 3. save the top 10 video IDs, video metadata of each channel to Redis
            Map<String, List<RedisYoutubeVideo>> videosByChannel = videoService.getTop10VideosByChannel(yt, channelIds);

            if (videosByChannel.isEmpty()) {
                log.warn("조회수 상위 10개 영상이 없습니다: userId={}", userId);
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

            // 4. save the comments metadata of each video to Redis(limit: 10)
            long totalCommentCount = commentService.syncTop10VideoComments(
                    userId, videosByChannel, SyncOptions.initialSync());

            // 5. save the transcripts for channel analysis (채널 성격 파악용 - 프로파일용 상위 10개 영상)
            List<String> allVideoIds = videosByChannel.values().stream()
                    .flatMap(List::stream)
                    .map(RedisYoutubeVideo::getYoutubeVideoId)
                    .collect(Collectors.toList());

            if (!allVideoIds.isEmpty()) {
                log.info("초기 동기화: {}개 영상의 자막 저장 시작 (채널 성격 파악용)", allVideoIds.size());
                youtubeTranscriptService.saveTranscriptsToRedis(allVideoIds, yt);
            }

            // 작업 큐에 채널별 작업 추가 (DB 1)
            log.info("🔄 작업 큐 추가 시작 (초기 동기화): userId={}, channelCount={}개", userId, videosByChannel.size());
            int enqueuedCount = 0;
            for (Map.Entry<String, List<RedisYoutubeVideo>> entry : videosByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<String> videoIds = entry.getValue().stream()
                        .map(RedisYoutubeVideo::getYoutubeVideoId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toList());

                if (!videoIds.isEmpty()) {
                    enqueueAgentTask(channelId, videoIds, "profiling");
                    enqueuedCount++;
                } else {
                    log.warn("⚠️ 채널 {}의 비디오 리스트가 비어있습니다. 작업 큐에 추가하지 않습니다.", channelId);
                }
            }

            log.info("✅ 작업 큐 추가 완료 (초기 동기화): userId={}, enqueuedCount={}개 채널", userId, enqueuedCount);
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
     * Redis 동기화 비동기 버전
     * 채널 저장 후 즉시 응답을 위해 백그라운드에서 실행됩니다.
     * 
     * 개선 사항:
     * 1. 동시 실행 제한: 동일 userId의 중복 실행 방지
     * 2. 상태 추적: DB에 동기화 상태 저장 (에러 추적)
     * 3. 데이터 정합성: Redis 동기화 전 채널 존재 여부 확인
     * 4. 안전한 에러 처리: CompletableFuture 완료 보장
     * 
     * @param userId 사용자 ID
     * @return CompletableFuture<RedisSyncResult> 비동기 동기화 결과
     */
    @Override
    @Async("redisSyncExecutor")
    public CompletableFuture<RedisSyncResult> syncToRedisAsync(Integer userId) {
        // 동시 실행 제한: 동일 userId의 중복 실행 방지
        if (syncInProgress.contains(userId)) {
            log.warn("⚠️ [비동기] Redis 동기화 이미 실행 중: userId={} (중복 요청 스킵)", userId);
            return CompletableFuture.completedFuture(
                    RedisSyncResult.builder()
                            .channelCount(0)
                            .videoCount(0)
                            .commentCount(0)
                            .success(false)
                            .errorMessage("이미 동기화가 진행 중입니다. 잠시 후 다시 시도해주세요.")
                            .build());
        }

        // 실행 중 표시 추가
        syncInProgress.add(userId);
        log.info("🔄 [비동기] Redis 동기화 시작: userId={} (실행 중인 작업: {}개)",
                userId, syncInProgress.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 데이터 정합성 체크: Redis 동기화 전 채널 존재 여부 확인
                List<YoutubeChannelDto> channels = youtubeChannelMapper.findByUserId(userId);
                if (channels == null || channels.isEmpty()) {
                    String errorMsg = "채널이 삭제되어 동기화를 중단합니다.";
                    log.warn("⚠️ [비동기] 데이터 정합성 체크 실패: userId={}, error={}", userId, errorMsg);

                    return RedisSyncResult.builder()
                            .channelCount(0)
                            .videoCount(0)
                            .commentCount(0)
                            .success(false)
                            .errorMessage(errorMsg)
                            .build();
                }

                log.debug("✅ [비동기] 데이터 정합성 체크 통과: userId={}, 채널수={}개",
                        userId, channels.size());

                // Redis 동기화 실행
                RedisSyncResult result = syncToRedis(userId);

                log.info("✅ [비동기] Redis 동기화 완료: userId={}, 채널={}개, 비디오={}개, 댓글={}개",
                        userId, result.getChannelCount(), result.getVideoCount(), result.getCommentCount());

                return result;

            } catch (Exception e) {
                log.error("❌ [비동기] Redis 동기화 실패: userId={}", userId, e);

                return RedisSyncResult.builder()
                        .channelCount(0)
                        .videoCount(0)
                        .commentCount(0)
                        .success(false)
                        .errorMessage(e.getMessage() != null ? e.getMessage() : "동기화 중 오류가 발생했습니다.")
                        .build();
            }
        }).whenComplete((result, ex) -> {
            // 실행 완료 후 제거 (성공/실패 모두)
            syncInProgress.remove(userId);
            log.debug("🧹 [비동기] Redis 동기화 완료 처리: userId={} (남은 작업: {}개)",
                    userId, syncInProgress.size());

            // 예외가 발생한 경우 추가 로깅
            if (ex != null) {
                log.error("❌ [비동기] CompletableFuture 예외 발생: userId={}", userId, ex);
            }
        });
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
     * @param userId   사용자 ID (OAuth 토큰 조회용)
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
            // ⚠️ 메타데이터 저장 실패해도 이미 Redis에 있을 수 있으므로, 작업 큐 추가는 시도
            int savedVideoCount = 0;
            boolean metadataSyncSuccess = true;
            try {
                savedVideoCount = videoService.syncVideoMetadata(userId, videoIds, incrementalOptions);
                if (savedVideoCount == 0) {
                    log.warn("⚠️ 비디오 메타데이터 저장 실패 (0개): userId={}, 이미 Redis에 있을 수 있음", userId);
                    metadataSyncSuccess = false;
                } else {
                    log.info("비디오 메타데이터 저장 성공: userId={}, videoCount={}개", userId, savedVideoCount);
                }
            } catch (Exception metadataEx) {
                metadataSyncSuccess = false;
                log.error("⚠️ 비디오 메타데이터 저장 실패: userId={}, error={}", userId, metadataEx.getMessage(), metadataEx);
                // ⚠️ 메타데이터 저장 실패해도 이미 Redis에 있을 수 있으므로 큐 추가는 진행
            }

            // 2단계: 비디오 댓글 저장 (전체 댓글, 제한 없음)
            // ⭐ API 호출: 각 비디오마다 댓글 조회 (옵션에 따라 제한 없음)
            long totalCommentCount = 0;
            boolean commentSyncSuccess = true;
            try {
                totalCommentCount = commentService.syncVideoComments(userId, videoIds, incrementalOptions);
                log.info("댓글 동기화 성공: userId={}, 댓글={}개", userId, totalCommentCount);
            } catch (Exception commentEx) {
                commentSyncSuccess = false;
                log.error("⚠️ 댓글 동기화 실패: userId={}, error={}", userId, commentEx.getMessage(), commentEx);
                // ⚠️ 댓글 실패해도 메타데이터는 저장되었으므로 큐 추가는 진행
            }

            // Redis에서 video 메타데이터를 조회하여 channelId별로 그룹화
            // ⚠️ 메타데이터/댓글 실패해도 이미 Redis에 있을 수 있으므로 큐 추가는 필수
            log.info("🔄 channelId별 그룹화 시작: userId={}, videoIds={}개", userId, videoIds.size());
            Map<String, List<String>> videoIdsByChannel = groupVideoIdsByChannel(videoIds);

            // 채널별로 작업 큐에 추가 (DB 1)
            log.info("🔄 작업 큐 추가 시작: userId={}, channelCount={}개", userId, videoIdsByChannel.size());
            int enqueuedCount = 0;
            for (Map.Entry<String, List<String>> entry : videoIdsByChannel.entrySet()) {
                String channelId = entry.getKey();
                List<String> channelVideoIds = entry.getValue();

                if (!channelVideoIds.isEmpty()) {
                    enqueueAgentTask(channelId, channelVideoIds, "filtering");
                    enqueuedCount++;
                } else {
                    log.warn("⚠️ 채널 {}의 비디오 리스트가 비어있습니다. 작업 큐에 추가하지 않습니다.", channelId);
                }
            }

            log.info("✅ 작업 큐 추가 완료: userId={}, enqueuedCount={}개 채널", userId, enqueuedCount);

            // 메타데이터/댓글 실패 여부에 따라 로그 및 성공 여부 결정
            if (!metadataSyncSuccess || !commentSyncSuccess) {
                if (!metadataSyncSuccess && !commentSyncSuccess) {
                    log.warn("⚠️ 메타데이터 및 댓글 동기화 실패했으나 작업 큐는 추가 시도: userId={}, 비디오={}개, 채널={}개",
                            userId, savedVideoCount, videoIdsByChannel.size());
                } else if (!metadataSyncSuccess) {
                    log.warn("⚠️ 메타데이터 동기화 실패했으나 댓글은 성공하고 작업 큐는 추가됨: userId={}, 비디오={}개, 채널={}개",
                            userId, savedVideoCount, videoIdsByChannel.size());
                } else {
                    log.warn("⚠️ 댓글 동기화 실패했으나 메타데이터는 저장되었고 작업 큐는 추가됨: userId={}, 비디오={}개, 채널={}개",
                            userId, savedVideoCount, videoIdsByChannel.size());
                }
            }

            // 작업 큐 추가 여부 확인
            if (videoIdsByChannel.isEmpty()) {
                log.error("❌ channelId별 그룹화 결과가 비어있습니다! 작업 큐에 추가되지 않았습니다. userId={}, videoIds={}개",
                        userId, videoIds.size());
            } else if (enqueuedCount == 0) {
                log.error("❌ 작업 큐에 추가된 채널이 0개입니다! userId={}, videoIdsByChannel={}개",
                        userId, videoIdsByChannel.size());
            }

            log.info("증분 Redis 동기화 완료: userId={}, 비디오={}개, 댓글={}개, 채널={}개, 메타성공={}, 댓글성공={}, 큐추가={}",
                    userId, savedVideoCount, totalCommentCount, videoIdsByChannel.size(),
                    metadataSyncSuccess, commentSyncSuccess, enqueuedCount > 0);

            return RedisSyncResult.builder()
                    .channelCount(videoIdsByChannel.size())
                    .videoCount(savedVideoCount)
                    .commentCount(totalCommentCount)
                    .success(metadataSyncSuccess && commentSyncSuccess && videoIdsByChannel.size() > 0) // 메타데이터 성공 + 댓글
                                                                                                        // 성공 + 큐 추가 성공
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
     * - Profiling: profiling_agent:tasks:queue
     * - Filtering: filtering_agent:tasks:queue
     * - Type: List
     * - Database: DB 1 (redisQueueTemplate)
     * - Spring 백엔드: LPUSH로 작업 추가 (왼쪽에 추가)
     * - FastAPI Agent: RPOP/BRPOP으로 작업 꺼내기 (오른쪽에서 꺼내기, read + delete 동시 수행)
     * 
     * LPUSH + RPOP 조합 = FIFO (First In First Out)
     * 
     * @param channelId YouTube 채널 ID
     * @param videoIds  처리할 비디오 ID 리스트
     * @param option    작업 옵션 ("profiling" 또는 "filtering")
     */
    private void enqueueAgentTask(String channelId, List<String> videoIds, String option) {
        try {
            if ("filtering".equals(option)) {
                // ⭐ Filtering Queue에 추가 (filtering_agent:tasks:queue)
                redisQueueService.enqueueFiltering(channelId, videoIds);
                log.info("✅ Filtering 작업 큐 추가: channelId={}, videoCount={}", channelId, videoIds.size());
            } else if ("profiling".equals(option)) {
                // ⭐ Profiling Queue에 추가 (profiling_agent:tasks:queue)
                redisQueueService.enqueueProfiling(channelId, videoIds);
                log.info("✅ Profiling 작업 큐 추가: channelId={}, videoCount={}",
                        channelId, videoIds != null ? videoIds.size() : 0);
            } else {
                log.warn("⚠️ 알 수 없는 작업 옵션: option={}, channelId={}", option, channelId);
            }
        } catch (Exception e) {
            log.error("❌ 작업 큐 추가 실패: channelId={}, option={}, error={}",
                    channelId, option, e.getMessage(), e);
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
        int estimatedChannels = Math.max(1, videoIds.size() / 10);
        Map<String, List<String>> result = new HashMap<>(estimatedChannels, 0.75f);

        log.info("🔍 channelId별 그룹화 시작: videoIds={}개", videoIds.size());

        int successCount = 0;
        int failCount = 0;

        for (String videoId : videoIds) {
            try {
                // Redis에서 비디오 메타데이터 조회
                String metaKey = "video:" + videoId + ":meta:json";
                String metaJson = stringRedisTemplate.opsForValue().get(metaKey);

                if (metaJson == null) {
                    log.warn("⚠️ 비디오 {}의 메타데이터가 Redis에 없습니다! key={}, MySQL에서 조회 시도", videoId, metaKey);

                    // MySQL fallback: Redis에 없으면 DB에서 조회하고 Redis에 저장
                    try {
                        // 1. 채널ID 조회
                        String youtubeChannelId = youtubeVideoMapper.findYoutubeChannelIdByVideoId(videoId);
                        if (youtubeChannelId == null || youtubeChannelId.isBlank()) {
                            log.warn("⚠️ MySQL에서도 비디오 {}의 channelId를 찾을 수 없습니다", videoId);
                            failCount++;
                            continue;
                        }

                        // 2. 비디오 정보 조회 (title 등 메타데이터용)
                        YoutubeVideoDto videoDto = youtubeVideoMapper.findByYoutubeVideoId(videoId);
                        if (videoDto == null) {
                            log.warn("⚠️ MySQL에서 비디오 {}의 정보를 찾을 수 없습니다", videoId);
                            // channelId는 있으니 작업 큐에는 추가하지만 Redis 저장은 스킵
                            result.computeIfAbsent(youtubeChannelId, k -> new java.util.ArrayList<>()).add(videoId);
                            successCount++;
                            log.info("✅ MySQL에서 channelId 조회 성공 (메타데이터 없음): videoId={}, channelId={}", videoId,
                                    youtubeChannelId);
                            continue;
                        }

                        // 3. RedisYoutubeVideo 객체 생성 (최소한의 메타데이터)
                        RedisYoutubeVideo redisVideo = RedisYoutubeVideo.builder()
                                .youtubeVideoId(videoDto.getYoutubeVideoId())
                                .title(videoDto.getTitle() != null ? videoDto.getTitle() : "")
                                .channelId(youtubeChannelId)
                                .tags(java.util.Collections.emptyList()) // MySQL에는 tags가 없음
                                .build();

                        // 4. Redis에 저장 (TTL 3일)
                        try {
                            String metaJsonFromDb = objectMapper.writeValueAsString(redisVideo);
                            stringRedisTemplate.opsForValue().set(metaKey, metaJsonFromDb);
                            stringRedisTemplate.expire(metaKey, java.time.Duration.ofDays(3));
                            log.info("✅ MySQL에서 조회한 메타데이터를 Redis에 저장 완료: videoId={}, channelId={}", videoId,
                                    youtubeChannelId);
                        } catch (Exception redisEx) {
                            log.warn("⚠️ Redis 메타데이터 저장 실패 (하지만 작업 큐에는 추가): videoId={}, error={}", videoId,
                                    redisEx.getMessage());
                            // Redis 저장 실패해도 작업 큐에는 추가
                        }

                        // 5. 작업 큐에 추가할 수 있도록 결과에 추가
                        result.computeIfAbsent(youtubeChannelId, k -> new java.util.ArrayList<>()).add(videoId);
                        successCount++;
                        log.info("✅ MySQL에서 channelId 조회 및 Redis 저장 성공: videoId={}, channelId={}", videoId,
                                youtubeChannelId);
                        continue;

                    } catch (Exception dbEx) {
                        log.error("❌ MySQL에서 channelId 조회 실패: videoId={}", videoId, dbEx);
                        failCount++;
                        continue;
                    }
                }

                log.debug("✅ 비디오 {} 메타데이터 조회 성공: {}", videoId, metaJson.substring(0, Math.min(100, metaJson.length())));

                // JSON 파싱하여 channelId 추출
                Map<String, Object> meta = objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {
                });
                String channelId = (String) meta.get("channel_id");

                if (channelId == null || channelId.isBlank()) {
                    log.warn("⚠️ 비디오 {}의 channel_id가 없거나 비어있습니다. meta={}", videoId, meta);
                    failCount++;
                    continue;
                }

                result.computeIfAbsent(channelId, k -> new java.util.ArrayList<>()).add(videoId);
                successCount++;
                log.debug("✅ 비디오 {} → channelId {} 매핑 완료", videoId, channelId);

            } catch (Exception e) {
                log.error("❌ 비디오 {}의 channelId 추출 실패", videoId, e);
                failCount++;
            }
        }

        log.info("🔍 channelId별 그룹화 완료: {}개 채널, 성공={}개, 실패={}개",
                result.size(), successCount, failCount);

        if (result.isEmpty()) {
            log.error("❌ 모든 비디오의 channelId 추출 실패! 작업 큐에 추가되지 않습니다. videoIds={}", videoIds);
        } else {
            for (Map.Entry<String, List<String>> entry : result.entrySet()) {
                log.info("📦 채널 {}: {}개 비디오", entry.getKey(), entry.getValue().size());
            }
        }

        return result;
    }
}
