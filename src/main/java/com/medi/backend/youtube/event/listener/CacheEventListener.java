package com.medi.backend.youtube.event.listener;

import com.medi.backend.youtube.event.ChannelCacheEvent;
import com.medi.backend.youtube.event.VideoCacheEvent;
import com.medi.backend.youtube.model.VideoSyncMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 캐시 이벤트 리스너
 * MySQL 트랜잭션 커밋 후 Redis 캐시 업데이트
 * 
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * - MySQL 커밋이 성공한 후에만 실행
 * - 커밋 실패 시 실행되지 않음 (데이터 일관성 보장)
 */
@Slf4j
@Component
public class CacheEventListener {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 채널 캐시 업데이트
     * MySQL 커밋 성공 후 Redis에 저장
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChannelCacheEvent(ChannelCacheEvent event) {
        try {
            log.debug("채널 캐시 업데이트 시작: {}", event.getYoutubeChannelId());

            // Redis에 채널 정보 저장
            String channelKey = "channel:" + event.getYoutubeChannelId();
            redisTemplate.opsForValue().set(channelKey, Map.of(
                    "id", event.getYoutubeChannelId(),
                    "title", event.getChannelName(),
                    "handle", event.getChannelHandle() != null ? event.getChannelHandle() : "",
                    "thumbnail", event.getThumbnailUrl() != null ? event.getThumbnailUrl() : ""
            ));

            // 사용자별 채널 목록에 추가
            if (event.getUserId() != null) {
                stringRedisTemplate.opsForSet().add(
                        "user:" + event.getUserId() + ":channels",
                        event.getYoutubeChannelId()
                );
            }

            log.debug("채널 캐시 업데이트 완료: {}", event.getYoutubeChannelId());

        } catch (Exception e) {
            log.error("채널 캐시 업데이트 실패: {}", event.getYoutubeChannelId(), e);
            // Redis 실패해도 MySQL은 이미 커밋되었으므로 예외를 던지지 않음
            // 필요시 재시도 로직 추가 가능
        }
    }

    /**
     * 영상 캐시 업데이트
     * MySQL 커밋 성공 후 Redis에 저장
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVideoCacheEvent(VideoCacheEvent event) {
        if (event.getSyncMode() == VideoSyncMode.REFRESH_ONLY) {
            log.debug("영상 캐시 업데이트 생략(REFRESH_ONLY): {}", event.getYoutubeVideoId());
            return;
        }
        try {
            log.debug("영상 캐시 업데이트 시작: {}", event.getYoutubeVideoId());

            // Redis에 영상 정보 저장
            String videoKey = "video:" + event.getYoutubeVideoId();
            redisTemplate.opsForValue().set(videoKey, Map.of(
                    "id", event.getYoutubeVideoId(),
                    "title", event.getTitle(),
                    "thumbnail", event.getThumbnailUrl() != null ? event.getThumbnailUrl() : "",
                    "publishedAt", event.getPublishedAt() != null ? event.getPublishedAt().toString() : ""
            ));

            log.debug("영상 캐시 업데이트 완료: {}", event.getYoutubeVideoId());

        } catch (Exception e) {
            log.error("영상 캐시 업데이트 실패: {}", event.getYoutubeVideoId(), e);
            // Redis 실패해도 MySQL은 이미 커밋되었으므로 예외를 던지지 않음
        }
    }
}

