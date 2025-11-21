package com.medi.backend.agent.service;

import com.medi.backend.agent.dto.AgentFilteredCommentsRequest;
import com.medi.backend.agent.dto.AgentProfilingRequest;
import com.medi.backend.agent.dto.FilteredCommentResponse;
import com.medi.backend.agent.dto.AnalysisSummaryResponse;

import java.util.List;

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
    
    /**
     * AI 프로파일링 결과를 DB에 저장
     * 
     * @param request AI 프로파일링 결과 (channelId 포함)
     * @return 저장 성공 여부 (1: 성공, 0: 실패)
     */
    Integer insertChannelProfiling(AgentProfilingRequest request);
    
    /**
     * 비디오별 필터링된 댓글 조회
     * 
     * @param videoId 내부 비디오 ID
     * @param userId 사용자 ID (권한 체크용)
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> getFilteredCommentsByVideoId(Integer videoId, Integer userId, String status);
    
    /**
     * 비디오별 분석 요약 조회
     * 
     * @param videoId 내부 비디오 ID
     * @param userId 사용자 ID (권한 체크용)
     * @return 분석 요약 정보 (없으면 null)
     */
    AnalysisSummaryResponse getAnalysisSummaryByVideoId(Integer videoId, Integer userId);
    
    /**
     * 채널별 필터링된 댓글 조회
     * 
     * @param channelId 내부 채널 ID
     * @param userId 사용자 ID (권한 체크용)
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> getFilteredCommentsByChannelId(Integer channelId, Integer userId, String status);
    
    /**
     * 사용자별 필터링된 댓글 조회
     * 
     * @param userId 사용자 ID
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> getFilteredCommentsByUserId(Integer userId, String status);
}

