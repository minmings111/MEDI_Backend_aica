package com.medi.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 분석 요약 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisSummaryResponse {
    
    private Integer videoId;
    private String youtubeVideoId;
    private String youtubeChannelId;
    
    // 통계
    private Integer neutralCount;
    private Integer filteredCount;
    private Integer suggestionCount;
    
    // 위험도 요약
    private String riskSummary;
    
    // 분석 시간
    private LocalDateTime analysisTimestamp;
}

