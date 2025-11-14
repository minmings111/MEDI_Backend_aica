package com.medi.backend.youtube.redis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Redis 동기화 결과 정보 DTO
 * 
 * 역할:
 * - Redis 동기화 작업의 결과를 담는 데이터 구조
 * - 동기화된 채널, 비디오, 댓글 개수와 성공 여부를 포함
 * 
 * 사용 위치:
 * - YoutubeRedisSyncService.syncToRedis() 메서드의 반환 타입
 */
@Getter
@Builder
public class RedisSyncResult {
    private final int channelCount;           // 처리된 채널 개수
    private final int videoCount;             // 처리된 비디오 개수
    private final long commentCount;          // 저장된 댓글 개수
    private final boolean success;            // 성공 여부
    private final String errorMessage;        // 에러 메시지 (실패 시)
}

