package com.medi.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일별 댓글 통계 DTO (daily_comment_stats 테이블 기반)
 * - 전체 댓글 수 (total_count)와 필터링된 댓글 수 (filtered_count) 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCommentStatDto {
    private String date;  // 날짜 (일별: "2025-01-15", 월별: "2025-01", 년별: "2025")
    private Integer totalCount;  // 전체 처리된 댓글 수 (neutral + filtered + suggestion)
    private Integer filteredCount;  // 필터링된 댓글 수
    private Integer normalCount;  // 일반 댓글 수 (totalCount - filteredCount)
}

