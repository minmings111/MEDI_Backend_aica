package com.medi.backend.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.dto.AgentProfilingRequest;
import com.medi.backend.agent.dto.FilteredCommentResponse;
import com.medi.backend.agent.dto.AnalysisSummaryResponse;
import com.medi.backend.agent.dto.FilteredCommentStatsResponse;
import com.medi.backend.agent.dto.DateStat;
import com.medi.backend.agent.mapper.AgentMapper;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

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
            
            try {
                int neutralCount = safeInt(request.getSentimentStats().getNeutral());
                int filteredCount = safeInt(request.getSentimentStats().getFiltered());
                int suggestionCount = safeInt(request.getSentimentStats().getSuggestion());
                int totalProcessed = neutralCount + filteredCount + suggestionCount;
                
                if (totalProcessed > 0) {
                    Integer internalChannelId = null;
                    if (request.getChannelId() != null && !request.getChannelId().isBlank()) {
                        internalChannelId = agentMapper.findChannelIdByYoutubeChannelId(request.getChannelId());
                    }
                    if (internalChannelId == null) {
                        internalChannelId = agentMapper.findChannelIdByVideoId(internalVideoId);
                    }
                    
                    if (internalChannelId != null) {
                        LocalDate statDate = resolveStatDate(request.getAnalysisTimestamp());
                        agentMapper.upsertDailyCommentStats(
                            internalChannelId,
                            internalVideoId,
                            statDate,
                            totalProcessed,
                            filteredCount
                        );
                        log.debug("Daily stats upserted: channelId={}, videoId={}, date={}, total={}, filtered={}",
                            internalChannelId, internalVideoId, statDate, totalProcessed, filteredCount);
                    } else {
                        log.warn("Unable to resolve channelId for daily stats: videoId={}, youtubeChannelId={}",
                            internalVideoId, request.getChannelId());
                    }
                } else {
                    log.debug("Skip daily stats upsert due to zero total count: videoId={}", internalVideoId);
                }
            } catch (Exception e) {
                log.error("Failed to upsert daily stats: videoId={}, youtubeChannelId={}",
                    internalVideoId, request.getChannelId(), e);
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
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByVideoId(Integer videoId, Integer userId, String status) {
        log.debug("비디오별 필터링된 댓글 조회: videoId={}, userId={}, status={}", videoId, userId, status);
        return agentMapper.findFilteredCommentsByVideoId(videoId, userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public AnalysisSummaryResponse getAnalysisSummaryByVideoId(Integer videoId, Integer userId) {
        log.debug("비디오별 분석 요약 조회: videoId={}, userId={}", videoId, userId);
        return agentMapper.findAnalysisSummaryByVideoId(videoId, userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByChannelId(Integer channelId, Integer userId, String status) {
        log.debug("채널별 필터링된 댓글 조회: channelId={}, userId={}, status={}", channelId, userId, status);
        return agentMapper.findFilteredCommentsByChannelId(channelId, userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<FilteredCommentResponse> getFilteredCommentsByUserId(Integer userId, String status) {
        log.debug("사용자별 필터링된 댓글 조회: userId={}, status={}", userId, status);
        return agentMapper.findFilteredCommentsByUserId(userId, status);
    }
    
    @Override
    @Transactional(readOnly = true)
    public FilteredCommentStatsResponse getFilteredCommentStatsByDate(
        Integer userId,
        Integer videoId,
        Integer channelId,
        String periodType,
        String startDate,
        String endDate
    ) {
        log.debug("날짜별 필터링된 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        // periodType 기본값 설정
        if (periodType == null || periodType.isBlank()) {
            periodType = "daily";
        }
        
        // 날짜별 통계 조회
        List<DateStat> stats = agentMapper.findFilteredCommentStatsByDate(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        // 전체 합계 계산
        int totalFiltered = 0;
        int totalSuggestions = 0;
        int totalNormal = 0;
        
        for (DateStat stat : stats) {
            totalFiltered += stat.getFilteredCount() != null ? stat.getFilteredCount() : 0;
            totalSuggestions += stat.getSuggestionCount() != null ? stat.getSuggestionCount() : 0;
            totalNormal += stat.getNormalCount() != null ? stat.getNormalCount() : 0;
        }
        
        return FilteredCommentStatsResponse.builder()
            .periodType(periodType)
            .stats(stats)
            .totalFiltered(totalFiltered)
            .totalSuggestions(totalSuggestions)
            .totalNormal(totalNormal)
            .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<com.medi.backend.agent.dto.DailyCommentStatDto> getDailyCommentStats(
        Integer userId,
        Integer videoId,
        Integer channelId,
        String periodType,
        String startDate,
        String endDate
    ) {
        log.debug("일별 전체 댓글 통계 조회: userId={}, videoId={}, channelId={}, periodType={}, startDate={}, endDate={}", 
            userId, videoId, channelId, periodType, startDate, endDate);
        
        // periodType 기본값 설정
        if (periodType == null || periodType.isBlank()) {
            periodType = "daily";
        }
        
        // daily_comment_stats 테이블에서 조회
        List<com.medi.backend.agent.dto.DailyCommentStatDto> stats = agentMapper.findDailyCommentStats(
            userId, videoId, channelId, periodType, startDate, endDate
        );
        
        log.info("✅ 일별 전체 댓글 통계 조회 완료: userId={}, 통계 항목수={}개", 
            userId, stats != null ? stats.size() : 0);
        
        return stats;
    }
    
    private LocalDate resolveStatDate(String analysisTimestamp) {
        if (analysisTimestamp == null || analysisTimestamp.isBlank()) {
            return LocalDate.now();
        }
        try {
            return OffsetDateTime.parse(analysisTimestamp).toLocalDate();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse analysisTimestamp for daily stats, fallback to today. timestamp={}, error={}",
                analysisTimestamp, e.getMessage());
            return LocalDate.now();
        }
    }
    
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}

