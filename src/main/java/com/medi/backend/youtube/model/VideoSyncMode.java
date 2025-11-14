package com.medi.backend.youtube.model;

/**
 * 비디오 동기화 시나리오 구분용 모드.
 * FIRST_SYNC : 채널 최초 동기화 (대량 수집 및 Redis 초기화)
 * FOLLOW_UP  : 이후 주기적인 증분 동기화
 * REFRESH_ONLY : 사용자가 새로고침할 때 DB만 갱신 (Redis는 건드리지 않음)
 */
public enum VideoSyncMode {
    FIRST_SYNC,
    FOLLOW_UP,
    REFRESH_ONLY
}

