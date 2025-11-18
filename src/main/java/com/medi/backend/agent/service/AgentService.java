package com.medi.backend.agent.service;

import java.util.List;

import com.medi.backend.agent.dto.AgentFilteredComment;

public interface AgentService {

    Integer findVideoIdByYoutubeVideoId(String youtubeVideoId);
    
    Integer insertFilteredComment(List<AgentFilteredComment> agentFilteredComments);

    
    

}
