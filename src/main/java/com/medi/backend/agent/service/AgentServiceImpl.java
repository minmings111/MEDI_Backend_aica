package com.medi.backend.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.dto.AgentProfilingRequest;
import com.medi.backend.agent.mapper.AgentMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper;
    
    public AgentServiceImpl(AgentMapper agentMapper, ObjectMapper objectMapper) {
        this.agentMapper = agentMapper;
        this.objectMapper = objectMapper;
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
        
        // 1. 요청에서 videoId 추출
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
        
        // 3. filteredComments 처리 (status = "filtered")
        if (request.getFilteredComments() != null) {
            for (AgentFilteredCommentsRequest.CommentData comment : request.getFilteredComments()) {
                savedCount += processComment(comment, internalVideoId, "filtered", request.getAnalysisTimestamp());
            }
        }
        
        // 4. contentSuggestions 처리 (status = "content_suggestion")
        if (request.getContentSuggestions() != null) {
            for (AgentFilteredCommentsRequest.CommentData comment : request.getContentSuggestions()) {
                savedCount += processComment(comment, internalVideoId, "content_suggestion", request.getAnalysisTimestamp());
            }
        }
        
        // 5. 분석 요약 데이터 저장
        if (request.getSentimentStats() != null) {
            try {
                agentMapper.insertAnalysisSummary(
                    internalVideoId,
                    request.getVideoId(),
                    request.getChannelId(),
                    request.getSentimentStats().getNeutral(),
                    request.getSentimentStats().getFiltered(),
                    request.getSentimentStats().getSuggestion(),
                    request.getRiskSummary(),
                    request.getAnalysisTimestamp()
                );
                log.debug("Analysis summary saved for video: {}", request.getVideoId());
            } catch (Exception e) {
                log.error("Failed to save analysis summary: videoId={}", request.getVideoId(), e);
            }
        }
        
        return savedCount;
    }
    
    private int processComment(AgentFilteredCommentsRequest.CommentData comment, Integer videoId, String status, String analyzedAt) {
        try {
            // 1. youtube_comments 테이블에 기본 댓글 정보 저장
            Integer insertResult = agentMapper.insertFilteredComment(
                videoId,
                comment.getCommentId(),
                comment.getTextOriginal(),
                comment.getAuthorName(),
                comment.getPublishedAt(),
                comment.getLikeCount()
            );
            
            log.debug("INSERT result: insertResult={}, youtubeCommentId={}", insertResult, comment.getCommentId());
            
            if (insertResult > 0) {
                // 2. 저장된 댓글의 id 조회
                Integer commentId = agentMapper.findCommentIdByYoutubeCommentId(comment.getCommentId());
                
                log.debug("SELECT result: commentId={}, youtubeCommentId={}", commentId, comment.getCommentId());
                
                if (commentId != null) {
                    // 3. ai_comment_analysis_result 테이블에 분석 결과 저장
                    Integer analysisResult = agentMapper.insertCommentAnalysisResult(
                        commentId,
                        status,
                        comment.getReason(),
                        analyzedAt
                    );
                    log.debug("Analysis result insert: result={}, commentId={}, status={}", 
                        analysisResult, commentId, status);
                    return 1;
                } else {
                    log.warn("Failed to find comment id after insert: videoId={}, youtubeCommentId={}", 
                        videoId, comment.getCommentId());
                }
            } else {
                log.warn("INSERT failed or no rows affected: insertResult={}, youtubeCommentId={}", 
                    insertResult, comment.getCommentId());
            }
        } catch (Exception e) {
            log.error("Failed to save comment: videoId={}, commentId={}, status={}", 
                videoId, comment.getCommentId(), status, e);
        }
        return 0;
    }
    
    @Override
    @Transactional
    public Integer insertChannelProfiling(AgentProfilingRequest request) {
        try {
            // 1. 요청에서 channelId 추출
            String youtubeChannelId = request.getChannelId();
            if (youtubeChannelId == null || youtubeChannelId.isBlank()) {
                log.warn("Channel ID is missing in request");
                return 0;
            }
            
            // 2. YouTube channel_id → 내부 channel_id 변환
            Integer internalChannelId = agentMapper.findChannelIdByYoutubeChannelId(youtubeChannelId);
            if (internalChannelId == null) {
                log.warn("Channel not found: {}", youtubeChannelId);
                return 0;
            }
            
            // 3. JSON 변환
            // profileData 전체를 JSON으로 변환
            String profileDataJson = objectMapper.writeValueAsString(request.getProfileData());
            
            // commentEcosystem만 추출하여 JSON으로 변환
            String commentEcosystemJson = "{}";  // 기본값: 빈 JSON 객체
            if (request.getProfileData() != null && request.getProfileData().getCommentEcosystem() != null) {
                commentEcosystemJson = objectMapper.writeValueAsString(request.getProfileData().getCommentEcosystem());
            }
            
            // channelCommunication만 추출하여 JSON으로 변환
            String channelCommunicationJson = "{}";  // 기본값: 빈 JSON 객체
            if (request.getProfileData() != null && request.getProfileData().getChannelCommunication() != null) {
                channelCommunicationJson = objectMapper.writeValueAsString(request.getProfileData().getChannelCommunication());
            }
            
            // metadata 전체를 JSON으로 변환
            String metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            
            // 4. metadata에서 주요 필드 추출
            String profilingCompletedAt = null;
            String version = null;
            if (request.getMetadata() != null) {
                profilingCompletedAt = request.getMetadata().getProfilingCompletedAt();
                version = request.getMetadata().getVersion();
            }
            
            // 5. ai_channel_profiling 테이블에 저장
            Integer result = agentMapper.insertChannelProfiling(
                internalChannelId,
                youtubeChannelId,
                profileDataJson,
                commentEcosystemJson,
                channelCommunicationJson,
                metadataJson,
                profilingCompletedAt,
                version
            );
            
            log.info("Channel profiling saved: channelId={}, youtubeChannelId={}, result={}", 
                internalChannelId, youtubeChannelId, result);
            
            return result != null && result > 0 ? 1 : 0;
            
        } catch (Exception e) {
            log.error("Failed to save channel profiling: channelId={}", request.getChannelId(), e);
            return 0;
        }
    }
}

