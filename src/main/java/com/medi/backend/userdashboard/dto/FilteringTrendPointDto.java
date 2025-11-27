package com.medi.backend.userdashboard.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 날짜별 필터링 추이 DTO
 * 
 * totalCount: YouTube Data API에서 가져온 실제 전체 댓글 수
 * filteredCount: 필터링된 댓글 수
 */
@Data
public class FilteringTrendPointDto {
    private LocalDate date;
    private Integer filteredCount;  // 필터링된 댓글 수
    private Long totalCount;       // YouTube 실제 전체 댓글 수 (BIGINT)
}

