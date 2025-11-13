package com.medi.backend.youtube.redis.service;

import java.util.List;
import com.medi.backend.youtube.redis.dto.YoutubeCommentForProfile;

public interface YoutubeCommentForProfileService {
    /**
     * 채널별 조회수 상위 20개 비디오의 댓글 내용만 추출하여 Redis에 저장
     * 
     * @param channelId 채널 ID
     * @param videoIds 해당 채널의 조회수 상위 20개 비디오 ID 리스트
     * @return 저장된 댓글 개수
     */
    long saveChannelComments(String channelId, List<String> videoIds);
    
    /**
     * 채널별 저장된 댓글 내용 조회
     * 
     * @param channelId 채널 ID
     * @return YoutubeCommentForProfile DTO (댓글 내용 리스트만)
     */
    YoutubeCommentForProfile getChannelComments(String channelId);
    
    /**
     * 채널별 저장된 댓글 내용 리스트만 조회
     * 
     * @param channelId 채널 ID
     * @return 댓글 내용 리스트
     */
    List<String> getChannelCommentTexts(String channelId);
}