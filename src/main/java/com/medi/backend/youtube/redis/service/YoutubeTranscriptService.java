package com.medi.backend.youtube.redis.service;

import java.util.List;

/**
 * YouTube 비디오 스크립트(자막) 저장 서비스 인터페이스
 * 
 * 목적:
 * - AI 분석을 위한 비디오 자막/대본 데이터 제공
 * - YouTube Captions API를 사용하여 자막 데이터 수집
 * 
 * Redis 저장 형식:
 * - Key: video:{video_id}:transcript
 * - Type: String
 * - Value: 스크립트 텍스트 원본 (예: "[음악]\n경민이 밖에 나가있을 때...")
 * 
 * 주의사항:
 * 1. YouTube Captions API는 별도의 할당량을 사용
 * 2. 모든 비디오에 자막이 있는 것은 아님 (자막 비활성화 또는 미제공)
 * 3. 자막은 자동 생성 자막과 수동 자막으로 구분됨
 * 4. 다국어 자막 지원 고려 필요
 * 
 * 구현 예정:
 * - 현재는 인터페이스만 정의
 * - 추후 YouTube Captions API 연동 필요 시 구현
 */
public interface YoutubeTranscriptService {
    
    /**
     * 특정 비디오의 스크립트(자막)를 Redis에 저장
     * 
     * @param videoId YouTube 비디오 ID
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @return 저장 성공 여부
     */
    boolean saveTranscriptToRedis(String videoId, Integer userId);
    
    /**
     * 여러 비디오의 스크립트(자막)를 Redis에 일괄 저장
     * 
     * @param videoIds YouTube 비디오 ID 목록
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @return 저장 성공한 비디오 개수
     */
    long saveTranscriptsToRedis(List<String> videoIds, Integer userId);
    
    /**
     * Redis에서 특정 비디오의 스크립트 조회
     * 
     * @param videoId YouTube 비디오 ID
     * @return 스크립트 텍스트 (없으면 null)
     */
    String getTranscriptFromRedis(String videoId);
}

