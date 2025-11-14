package com.medi.backend.youtube.redis.service;

import java.util.List;
import java.util.Map;

import com.medi.backend.youtube.redis.dto.RedisYoutubeVideo;
import com.medi.backend.youtube.redis.dto.SyncOptions;

/**
 * 조회수 상위 20개 영상의 댓글을 Redis에 저장하는 서비스
 */
public interface YoutubeCommentService {
    
    /**
     * 각 채널별 조회수 상위 20개 영상의 댓글 동기화
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videosByChannel 채널별 비디오 리스트 (이미 조회된 결과를 재사용하여 중복 API 호출 방지)
     * @param options 동기화 옵션 (댓글 개수 제한 등)
     * @return Redis에 저장된 댓글 개수
     */
    long syncTop20VideoComments(Integer userId, Map<String, List<RedisYoutubeVideo>> videosByChannel, SyncOptions options);
    
    /**
     * 각 채널별 조회수 상위 20개 영상의 댓글 동기화 (기본 옵션 사용)
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videosByChannel 채널별 비디오 리스트
     * @return Redis에 저장된 댓글 개수
     */
    default long syncTop20VideoComments(Integer userId, Map<String, List<RedisYoutubeVideo>> videosByChannel) {
        return syncTop20VideoComments(userId, videosByChannel, SyncOptions.initialSync());
    }
    
    /**
     * 특정 비디오들의 댓글 동기화 (증분 동기화용)
     * 
     * @param userId 사용자 ID (OAuth 토큰 조회용)
     * @param videoIds 비디오 ID 리스트
     * @param options 동기화 옵션 (증분 동기화는 전체 댓글 가져오기)
     * @return Redis에 저장된 댓글 개수
     */
    long syncVideoComments(Integer userId, List<String> videoIds, SyncOptions options);
}
