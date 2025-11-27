package com.medi.backend.agent.mapper;

import com.medi.backend.agent.dto.FilteredCommentResponse;
import com.medi.backend.agent.dto.AnalysisSummaryResponse;
import com.medi.backend.agent.dto.DateStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
    
    /**
     * YouTube channel_id로 내부 channel_id 조회
     */
    Integer findChannelIdByYoutubeChannelId(@Param("youtubeChannelId") String youtubeChannelId);
    
    /**
     * 내부 video_id로 channel_id 조회
     */
    Integer findChannelIdByVideoId(@Param("videoId") Integer videoId);
    
    /**
     * ai_channel_profiling 테이블에 프로파일링 결과 저장
     */
    Integer insertChannelProfiling(
        @Param("channelId") Integer channelId,
        @Param("youtubeChannelId") String youtubeChannelId,
        @Param("profileData") String profileDataJson,  // profileData 전체 JSON
        @Param("commentEcosystem") String commentEcosystemJson,  // commentEcosystem JSON
        @Param("channelCommunication") String channelCommunicationJson,  // channelCommunication JSON
        @Param("metadata") String metadataJson  // metadata 전체 JSON
    );
    
    /**
     * 비디오별 필터링된 댓글 조회
     * 
     * @param videoId 내부 비디오 ID
     * @param userId 사용자 ID (권한 체크용)
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> findFilteredCommentsByVideoId(
        @Param("videoId") Integer videoId,
        @Param("userId") Integer userId,
        @Param("status") String status
    );
    
    /**
     * 비디오별 분석 요약 조회
     * 
     * @param videoId 내부 비디오 ID
     * @param userId 사용자 ID (권한 체크용)
     * @return 분석 요약 정보
     */
    AnalysisSummaryResponse findAnalysisSummaryByVideoId(
        @Param("videoId") Integer videoId,
        @Param("userId") Integer userId
    );
    
    /**
     * 채널별 필터링된 댓글 조회
     * 
     * @param channelId 내부 채널 ID
     * @param userId 사용자 ID (권한 체크용)
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> findFilteredCommentsByChannelId(
        @Param("channelId") Integer channelId,
        @Param("userId") Integer userId,
        @Param("status") String status
    );
    
    /**
     * 사용자별 필터링된 댓글 조회
     * 
     * @param userId 사용자 ID
     * @param status 필터링 상태 (filtered, content_suggestion, normal) - null이면 전체
     * @return 필터링된 댓글 목록
     */
    List<FilteredCommentResponse> findFilteredCommentsByUserId(
        @Param("userId") Integer userId,
        @Param("status") String status
    );
    
    /**
     * 날짜별 필터링된 댓글 통계 조회
     * 
     * @param userId 사용자 ID
     * @param videoId 비디오 ID (선택사항, null이면 전체)
     * @param channelId 채널 ID (선택사항, null이면 전체)
     * @param periodType 날짜 단위 ("daily", "monthly", "yearly")
     * @param startDate 시작 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @param endDate 종료 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @return 날짜별 통계 목록
     */
    List<DateStat> findFilteredCommentStatsByDate(
        @Param("userId") Integer userId,
        @Param("videoId") Integer videoId,
        @Param("channelId") Integer channelId,
        @Param("periodType") String periodType,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );
    
    /**
     * daily_comment_stats 테이블에 일별 통계 upsert
     */
    int upsertDailyCommentStats(
        @Param("channelId") Integer channelId,
        @Param("videoId") Integer videoId,
        @Param("statDate") java.time.LocalDate statDate,
        @Param("totalCount") Integer totalCount,
        @Param("filteredCount") Integer filteredCount
    );
    
    /**
     * 시간별 필터링된 댓글 개수 조회 (이메일 알림 체크용)
     * - 기존 테이블에서 직접 COUNT (테이블 추가 불필요)
     * - 특정 시간대(시 단위)에 필터링된 댓글 개수를 반환
     */
    Integer getHourlyFilteredCount(
        @Param("channelId") Integer channelId,
        @Param("statDatetime") java.time.LocalDateTime statDatetime
    );
    
    /**
     * YouTube 실제 댓글 수 업데이트 (스케줄러에서 사용)
     */
    int updateYoutubeTotalCount(
        @Param("channelId") Integer channelId,
        @Param("videoId") Integer videoId,
        @Param("statDate") java.time.LocalDate statDate,
        @Param("youtubeTotalCount") Long youtubeTotalCount
    );
    
    /**
     * daily_comment_stats 테이블에서 일별 통계 조회
     * - 전체 댓글 수 (total_count)와 필터링된 댓글 수 (filtered_count) 포함
     * 
     * @param userId 사용자 ID
     * @param videoId 비디오 ID (선택사항, null이면 전체)
     * @param channelId 채널 ID (선택사항, null이면 전체)
     * @param periodType 날짜 단위 ("daily", "monthly", "yearly")
     * @param startDate 시작 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @param endDate 종료 날짜 (선택사항, 형식: "YYYY-MM-DD")
     * @return 날짜별 통계 목록
     */
    List<com.medi.backend.agent.dto.DailyCommentStatDto> findDailyCommentStats(
        @Param("userId") Integer userId,
        @Param("videoId") Integer videoId,
        @Param("channelId") Integer channelId,
        @Param("periodType") String periodType,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );
}

