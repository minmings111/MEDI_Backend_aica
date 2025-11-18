package com.medi.backend.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.agent.dto.AgentFilteredComment;
import com.medi.backend.agent.mapper.AgentMapper;

@Service
public class AgentServiceImpl implements AgentService{

    private final AgentMapper agentMapper;
    
    public AgentServiceImpl(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findVideoIdByYoutubeVideoId(String youtubeVideoId) {
        return agentMapper.findVideoIdByYoutubeVideoId(youtubeVideoId);
    }

    @Override
    @Transactional
    public Integer insertFilteredComment(List<AgentFilteredComment> agentFilteredComments) {
        int savedCount = 0;

        for (AgentFilteredComment comment : agentFilteredComments) {
            // 1. videoId(String) → Integer
            Integer videoId = findVideoIdByYoutubeVideoId(comment.getVideoId());
            
            // 2. save to youtube_comments of DB
            Integer result = agentMapper.insertFilteredComment(
                videoId,
                comment.getCommentId(),
                comment.getTextOriginal(),
                comment.getAuthorName(),
                comment.getPublishedAt(),
                comment.getLikeCount(),
                comment.getAuthorChannelId(),
                comment.getUpdatedAt(),
                comment.getParentId(),
                comment.getTotalReplyCount(),
                comment.getCanRate(),
                comment.getViewerRating()
            );
            
            if (result > 0) {
                savedCount++;
            }
        }
        
        return savedCount;
    }


    
    
}
