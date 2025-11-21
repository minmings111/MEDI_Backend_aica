package com.medi.backend.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.mapper.AgentMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

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
    public Integer insertFilteredComment(AgentFilteredCommentsRequest request) {
        int savedCount = 0;
        
        // 1. 요청에서 video_id 추출
        String videoId = request.getVideoId();
        if (videoId == null || videoId.isBlank()) {
            log.warn("Video ID is missing in request");
            return 0;
        }
        
        // 2. YouTube video_id → 내부 video_id 변환
        Integer internalVideoId = findVideoIdByYoutubeVideoId(videoId);
        if (internalVideoId == null) {
            log.warn("Video not found: {}", videoId);
            return 0;
        }
        
        if (request.getComments() == null || request.getComments().isEmpty()) {
            log.warn("No comments in request for video: {}", videoId);
            return 0;
        }
        
        for (AgentFilteredCommentsRequest.CommentData comment : request.getComments()) {
            try {
                // 3. status 결정: reason이 있으면 "filtered", 없으면 "normal"
                String status = (comment.getReason() != null && !comment.getReason().isEmpty()) 
                    ? "filtered" 
                    : "normal";
                
                // 4. youtube_comments 테이블에 기본 댓글 정보 저장
                Integer insertResult = agentMapper.insertFilteredComment(
                    internalVideoId,
                    comment.getCommentId(),
                    comment.getTextOriginal(),
                    comment.getAuthorName(),
                    comment.getPublishedAt(),
                    comment.getLikeCount()
                );
                
                log.debug("INSERT result: insertResult={}, youtubeCommentId={}", insertResult, comment.getCommentId());
                
                if (insertResult > 0) {
                    // 5. 저장된 댓글의 id 조회 (youtube_comment_id는 UNIQUE이므로 video_id 조건 불필요)
                    Integer commentId = agentMapper.findCommentIdByYoutubeCommentId(
                        comment.getCommentId()
                    );
                    
                    log.debug("SELECT result: commentId={}, youtubeCommentId={}", commentId, comment.getCommentId());
                    
                    if (commentId != null) {
                        // 6. ai_comment_analysis_result 테이블에 분석 결과 저장
                        Integer analysisResult = agentMapper.insertCommentAnalysisResult(
                            commentId,
                            status,
                            comment.getReason(),
                            request.getAnalyzedAt()
                        );
                        log.debug("Analysis result insert: result={}, commentId={}, status={}", 
                            analysisResult, commentId, status);
                        savedCount++;
                    } else {
                        log.warn("Failed to find comment id after insert: videoId={}, youtubeCommentId={}", 
                            internalVideoId, comment.getCommentId());
                    }
                } else {
                    log.warn("INSERT failed or no rows affected: insertResult={}, youtubeCommentId={}", 
                        insertResult, comment.getCommentId());
                }
            } catch (Exception e) {
                log.error("Failed to save filtered comment: videoId={}, commentId={}", 
                    videoId, comment.getCommentId(), e);
            }
        }
        
        return savedCount;
    }
}

