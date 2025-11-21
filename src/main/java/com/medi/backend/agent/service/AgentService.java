package com.medi.backend.agent.service;

import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;

public interface AgentService {
    
    /**
     * YouTube video_id로 내부 video_id 조회
     */
    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);
    
    /**
     * AI 분석 결과를 DB에 저장
     * 
     * @param request AI 분석 결과 (video_id 포함)
     * @return 저장된 댓글 개수
     */
    Integer insertFilteredComment(AgentFilteredCommentsRequest request);
}

