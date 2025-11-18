package com.medi.backend.agent.service;

import java.util.List;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.dto.AgentIndividualResult;

public interface AgentService {

    // find int ID(using in mysql) by string youtube ID
    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);
    Integer findChannelIdByYoutubeChannelId(String youtubeChannelId);
    
    Integer findUserIdByChannelId(Integer channelId);
    
    Integer insertFilteredComment(List<AgentFilteredComment> agentFilteredComments);
    
    // 개별 분석 결과 수집 및 저장
    void collectIndividualResult(AgentIndividualResult result);

}
