package com.medi.backend.youtube.scheduler;

import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.mapper.YoutubeChannelMapper;
import com.medi.backend.youtube.model.VideoSyncMode;
import com.medi.backend.youtube.service.YoutubeService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매 시간마다 등록된 채널의 영상을 동기화하는 스케줄러.
 * 채널마다 일정 간격을 두어 API 호출이 몰리지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSyncScheduler {

    private final YoutubeChannelMapper youtubeChannelMapper;
    private final YoutubeService youtubeService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void syncAllChannelsDaily() {
        List<YoutubeChannelDto> channels = youtubeChannelMapper.findAllForSync();
        if (channels == null || channels.isEmpty()) {
            log.debug("[YouTube] 스케줄링 동기화 - 동기화할 채널이 없음");
            return;
        }

        log.info("[YouTube] 스케줄링 동기화 시작 - 대상 채널 수={}", channels.size());

        for (YoutubeChannelDto channel : channels) {
            Integer userId = channel.getUserId();
            String youtubeChannelId = channel.getYoutubeChannelId();

            if (userId == null || youtubeChannelId == null) {
                log.warn("[YouTube] 스케줄링 동기화 - userId 또는 channelId 누락: {}", channel);
                continue;
            }

            try {
                youtubeService.syncVideos(userId, youtubeChannelId, null, VideoSyncMode.FOLLOW_UP);
                log.debug("[YouTube] 스케줄링 동기화 성공 - userId={}, channelId={}", userId, youtubeChannelId);
            } catch (Exception ex) {
                log.warn("[YouTube] 스케줄링 동기화 실패 - userId={}, channelId={}, message={}",
                        userId, youtubeChannelId, ex.getMessage(), ex);
            }

            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[YouTube] 스케줄링 동기화 중단 - 인터럽트 발생");
                return;
            }
        }

        log.info("[YouTube] 스케줄링 동기화 종료");
    }
}

