package com.medi.backend.agent.service;

import java.util.List;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentChannelResult;

public interface AgentService {

    // find int ID(using in mysql) by string youtube ID
    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);
    Integer findChannelIdByYoutubeChannelId(String youtubeChannelId);
    
    Integer findUserIdByChannelId(Integer channelId);
    
    // 유틸리티 메서드: ID 변환
    Integer findChannelIdByVideoId(Integer videoId);
    String findYoutubeChannelIdByChannelId(Integer channelId);
    
    // insert filtered comments to DB
    Integer insertFilteredComment(List<AgentFilteredComment> agentFilteredComments);
    
    // 채널별 통합 분석 결과 저장 (채널별로 모든 결과가 묶여서 전달됨)
    void saveChannelResult(AgentChannelResult result);

}
