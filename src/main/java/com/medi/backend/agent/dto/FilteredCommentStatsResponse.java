package com.medi.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 날짜별 필터링된 댓글 통계 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilteredCommentStatsResponse {
    
    private String periodType;  // "daily", "monthly", "yearly"
    private List<DateStat> stats;  // 날짜별 통계 목록
    private Integer totalFiltered;  // 전체 필터링된 댓글 수 (전체 기간)
    private Integer totalSuggestions;  // 전체 제안 댓글 수 (전체 기간)
    private Integer totalNormal;  // 전체 정상 댓글 수 (전체 기간)
}

