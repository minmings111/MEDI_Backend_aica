package com.medi.backend.youtube.redis.service;

/**
 * 조회수 상위 20개 영상의 댓글을 Redis에 저장하는 서비스
 */
public interface YoutubeCommentService {
    
    /**
     * 사용자의 등록된 채널에서 각 채널마다 조회수 상위 20개 영상의 댓글을 Redis에 저장
     * 
     * @param userId 사용자 ID
     * @return Redis에 저장된 댓글 개수
     */
    long syncTop20VideoComments(Integer userId);
}
