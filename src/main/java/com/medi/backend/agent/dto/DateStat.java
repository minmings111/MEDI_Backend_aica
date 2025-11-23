package com.medi.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 날짜별 통계 항목 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateStat {
    private String date;  // 날짜 (일별: "2025-01-15", 월별: "2025-01", 년별: "2025")
    private Integer totalCount;  // 전체 필터링된 댓글 수
    private Integer filteredCount;  // status='filtered'인 댓글 수
    private Integer suggestionCount;  // status='content_suggestion'인 댓글 수
    private Integer normalCount;  // status='normal'인 댓글 수
}

