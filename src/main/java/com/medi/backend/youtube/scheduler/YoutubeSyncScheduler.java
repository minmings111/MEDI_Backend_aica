package com.medi.backend.youtube.scheduler;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeOAuthTokenDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.mapper.YoutubeOAuthTokenMapper;
import com.medi.backend.youtube.mapper.YoutubeVideoMapper;
import com.medi.backend.youtube.model.VideoSyncMode;
import com.medi.backend.youtube.redis.service.YoutubeRedisSyncService;
import com.medi.backend.youtube.service.YoutubeCommentCountSyncService;
import com.medi.backend.youtube.service.YoutubeService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매 시간마다 등록된 채널의 정보와 영상을 동기화하는 스케줄러.
 * - 채널 정보 동기화: 구독자 수, 채널명 등 채널 정보 최신화
 * - 영상 동기화: 새 영상 및 댓글 동기화
 * 채널마다 일정 간격을 두어 API 호출이 몰리지 않도록 한다.
 * 토큰이 만료된 채널은 스킵하여 불필요한 API 호출을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSyncScheduler {

    private final YoutubeChannelMapper youtubeChannelMapper;
    private final YoutubeVideoMapper youtubeVideoMapper;
    private final YoutubeService youtubeService;
    private final YoutubeOAuthTokenMapper tokenMapper;
    private final YoutubeRedisSyncService youtubeRedisSyncService;
    private final YoutubeCommentCountSyncService youtubeCommentCountSyncService;

    // 동시 실행 채널 수 제한 (메모리 및 CPU 부하 방지)
    private static final int MAX_CONCURRENT_CHANNELS = 3;
    private final Semaphore channelSemaphore = new Semaphore(MAX_CONCURRENT_CHANNELS);

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void syncAllChannelsDaily() {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        double usagePercent = (double) usedMemory / maxMemory * 100;

        log.info("📊 [스케줄러] 시작 전 메모리 상태: 사용률={}%, 사용={}MB, 최대={}MB",
                String.format("%.2f", usagePercent), usedMemory / (1024 * 1024), maxMemory / (1024 * 1024));

        if (usagePercent > 80) {
            log.error("🚨 [스케줄러] 메모리 부족 ({}%) - 동기화 건너뜀", String.format("%.2f", usagePercent));
            return;
        }

        List<YoutubeChannelDto> channels = youtubeChannelMapper.findAllForSync();
        if (channels == null || channels.isEmpty()) {
            log.debug("[YouTube] 스케줄링 동기화 - 동기화할 채널이 없음");
            return;
        }

        log.info("[YouTube] 스케줄링 동기화 시작 - 대상 채널 수={}", channels.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (YoutubeChannelDto channel : channels) {
            Integer userId = channel.getUserId();
            String youtubeChannelId = channel.getYoutubeChannelId();

            if (userId == null || youtubeChannelId == null) {
                log.warn("[YouTube] 스케줄링 동기화 - userId 또는 channelId 누락: {}", channel);
                continue;
            }

            // Semaphore로 동시 실행 제한 (최대 3개 채널)
            if (!channelSemaphore.tryAcquire()) {
                log.warn("⚠️ [스케줄러] 동시 실행 채널 제한 도달 (최대 {}개) - userId={}, channelId={} 건너뜀 (다음 스케줄에서 재시도)",
                        MAX_CONCURRENT_CHANNELS, userId, youtubeChannelId);
                skipCount++;
                continue;
            }

            try {
                // 토큰 상태 확인 - 만료된 토큰의 채널은 스킵
                YoutubeOAuthTokenDto token = tokenMapper.findByUserId(userId);
                if (token == null) {
                    log.warn("[YouTube] 스케줄링 동기화 스킵 - 토큰 없음: userId={}, channelId={}", userId, youtubeChannelId);
                    skipCount++;
                    continue;
                }

                if ("EXPIRED".equals(token.getTokenStatus())) {
                    log.debug("[YouTube] 스케줄링 동기화 스킵 - 토큰 만료: userId={}, channelId={} (사용자가 재연결 필요)",
                            userId, youtubeChannelId);
                    skipCount++;
                    continue;
                }
                // 0. 채널 정보 동기화 (구독자 수 등 채널 정보 최신화)
                try {
                    youtubeService.syncChannels(userId, false);
                    log.debug("[YouTube] 스케줄링 채널 정보 동기화 완료 - userId={}, channelId={} (구독자 수 등 업데이트됨)",
                            userId, youtubeChannelId);
                } catch (Exception channelSyncEx) {
                    log.warn("[YouTube] 스케줄링 채널 정보 동기화 실패 (영상 동기화는 계속 진행) - userId={}, channelId={}, error={}",
                            userId, youtubeChannelId, channelSyncEx.getMessage());
                    // 채널 정보 동기화 실패해도 영상 동기화는 계속 진행
                }

                // 1. 새 영상 동기화 (MySQL 저장만, 댓글 동기화 건너뜀)
                // skipCommentSync=true로 설정하여 중복 API 호출 방지
                List<YoutubeVideoDto> newVideos = youtubeService.syncVideos(
                        userId, youtubeChannelId, null, VideoSyncMode.FOLLOW_UP, true);

                log.debug("[YouTube] 스케줄링 동기화 성공 - userId={}, channelId={}, 새 영상={}개",
                        userId, youtubeChannelId, newVideos != null ? newVideos.size() : 0);

                // 2. MySQL에 저장된 "모든 영상" 댓글 동기화 (초기 20개 + 누적된 신규 영상)
                // (사용자 요구사항: 등록된 영상들은 계속해서 댓글 필터링)
                List<YoutubeVideoDto> allVideos = youtubeVideoMapper.findByChannelId(channel.getId());
                if (!allVideos.isEmpty()) {

                    // B. DB에 저장된 모든 영상 (초기 20개 + 누적된 신규 영상)
                    // limit 없이 전체를 대상으로 함 (사용자 요구사항: 누적된 영상 모두 필터링)
                    List<YoutubeVideoDto> accumulatedVideos = allVideos;

                    // C. 합집합 (중복 제거)
                    java.util.Set<String> targetVideoIds = new java.util.HashSet<>();

                    for (YoutubeVideoDto v : accumulatedVideos) {
                        if (v.getYoutubeVideoId() != null)
                            targetVideoIds.add(v.getYoutubeVideoId());
                    }

                    List<String> finalVideoIds = new ArrayList<>(targetVideoIds);

                    if (!finalVideoIds.isEmpty()) {
                        try {
                            log.info(
                                    "[YouTube] 댓글 동기화 대상 선정: userId={}, channelId={}, 대상={}개 (누적된 전체 영상)",
                                    userId, youtubeChannelId, finalVideoIds.size());

                            var syncResult = youtubeRedisSyncService.syncIncrementalToRedis(userId, finalVideoIds);

                            if (syncResult.isSuccess()) {
                                log.info("[YouTube] 댓글 동기화 완료: userId={}, 비디오={}개, 댓글={}개, 채널={}개, 큐추가됨={}",
                                        userId, syncResult.getVideoCount(), syncResult.getCommentCount(),
                                        syncResult.getChannelCount(), syncResult.getChannelCount() > 0);
                            } else {
                                log.warn("[YouTube] 댓글 동기화 부분 실패: userId={}, 비디오={}개, 댓글={}개, 채널={}개, error={}",
                                        userId, syncResult.getVideoCount(), syncResult.getCommentCount(),
                                        syncResult.getChannelCount(), syncResult.getErrorMessage());
                            }

                            // ⚠️ 큐 추가 여부 확인 (큐 추가는 댓글 실패해도 실행됨)
                            if (syncResult.getChannelCount() == 0) {
                                log.error("❌ [YouTube] 작업 큐에 추가되지 않았습니다! userId={}, channelId={}, videoIds={}개",
                                        userId, youtubeChannelId, finalVideoIds.size());
                            }
                        } catch (Exception e) {
                            log.error("❌ [YouTube] 댓글 동기화 실패: userId={}, channelId={}, error={}",
                                    userId, youtubeChannelId, e.getMessage(), e);
                            // 댓글 동기화 실패해도 영상 동기화는 성공한 것으로 간주
                        }
                    }
                }

                successCount++;
            } catch (Exception ex) {
                // 토큰 만료 관련 예외인지 확인
                String errorMessage = ex.getMessage();
                Throwable cause = ex.getCause();
                if (errorMessage != null && (errorMessage.contains("Refresh token expired")
                        || errorMessage.contains("invalid_grant")
                        || (cause != null && cause.getMessage() != null
                                && cause.getMessage().contains("invalid_grant")))) {
                    log.warn("[YouTube] 스케줄링 동기화 스킵 - 토큰 만료로 인한 실패: userId={}, channelId={} (다음 동기화부터 자동 스킵됨)",
                            userId, youtubeChannelId);
                    skipCount++;
                } else {
                    log.warn("[YouTube] 스케줄링 동기화 실패 - userId={}, channelId={}, message={}",
                            userId, youtubeChannelId, ex.getMessage(), ex);
                    failCount++;
                }
            } finally {
                // Semaphore 반드시 release (예외 발생해도 실행)
                channelSemaphore.release();
            }

            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[YouTube] 스케줄링 동기화 중단 - 인터럽트 발생");
                return;
            }
        }

        log.info("[YouTube] 스케줄링 동기화 종료 - 성공: {}, 스킵: {}, 실패: {}", successCount, skipCount, failCount);

        long finalUsedMemory = runtime.totalMemory() - runtime.freeMemory();
        double finalUsagePercent = (double) finalUsedMemory / maxMemory * 100;
        log.info("📊 [스케줄러] 완료 후 메모리 상태: 사용률={}%, 사용={}MB, 최대={}MB",
                String.format("%.2f", finalUsagePercent), finalUsedMemory / (1024 * 1024), maxMemory / (1024 * 1024));
    }

    /**
     * 하루에 한 번 YouTube 실제 댓글 수를 daily_comment_stats 테이블에 저장
     * 매일 오전 1시에 실행 (Asia/Seoul 시간대)
     * - 자정에는 다른 작업이 실행될 수 있어 충돌 방지를 위해 1시로 설정
     * - 전날의 댓글 수를 저장하여 날짜별 추적 가능
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void syncYoutubeCommentCountsDaily() {
        log.info("📊 [스케줄러] YouTube 실제 댓글 수 동기화 시작");
        try {
            youtubeCommentCountSyncService.syncYoutubeCommentCounts();
            log.info("✅ [스케줄러] YouTube 실제 댓글 수 동기화 완료");
        } catch (Exception e) {
            log.error("❌ [스케줄러] YouTube 실제 댓글 수 동기화 실패", e);
        }
    }
}
