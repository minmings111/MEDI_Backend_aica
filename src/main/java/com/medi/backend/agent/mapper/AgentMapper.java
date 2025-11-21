package com.medi.backend.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentMapper {
    
    /**
     * YouTube video_id로 내부 video_id 조회
     */
    Integer findVideoIdByYoutubeVideoId(@Param("youtubeVideoId") String youtubeVideoId);
    
    /**
     * youtube_comments 테이블에 댓글 저장
     */
    Integer insertFilteredComment(
        @Param("videoId") Integer videoId,
        @Param("youtubeCommentId") String youtubeCommentId,
        @Param("commentText") String commentText,
        @Param("commenterName") String commenterName,
        @Param("publishedAt") String publishedAt,
        @Param("likeCount") Long likeCount
    );
    
    /**
     * 저장된 댓글의 id 조회
     * youtube_comment_id는 테이블 전체에서 UNIQUE이므로 video_id 조건 불필요
     */
    Integer findCommentIdByYoutubeCommentId(
        @Param("youtubeCommentId") String youtubeCommentId
    );
    
    /**
     * ai_comment_analysis_result 테이블에 분석 결과 저장
     */
    Integer insertCommentAnalysisResult(
        @Param("youtubeCommentId") Integer youtubeCommentId,  // youtube_comments.id
        @Param("status") String status,  // "filtered", "content_suggestion", "normal"
        @Param("reason") String reason,  // AI 서버의 reason 값
        @Param("analyzedAt") String analyzedAt  // AI 서버의 analyzed_at 값
    );
    
    /**
     * ai_analysis_summary 테이블에 분석 요약 저장
     */
    Integer insertAnalysisSummary(
        @Param("videoId") Integer videoId,
        @Param("youtubeVideoId") String youtubeVideoId,
        @Param("youtubeChannelId") String youtubeChannelId,
        @Param("neutralCount") Integer neutralCount,
        @Param("filteredCount") Integer filteredCount,
        @Param("suggestionCount") Integer suggestionCount,
        @Param("riskSummary") String riskSummary,
        @Param("analysisTimestamp") String analysisTimestamp
    );
}

