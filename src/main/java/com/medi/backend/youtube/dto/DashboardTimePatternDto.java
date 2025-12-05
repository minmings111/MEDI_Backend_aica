package com.medi.backend.youtube.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 대시보드 시간대별 악플 통계 DTO (DB 매핑)
 * 
 * 테이블: dashboard_time_pattern_stats
 * 용도: 대시보드 OverviewTab의 "악플 집중 시간대" 그래프 데이터
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTimePatternDto {

    private Integer id;
    private Integer channelId;

    // 시간대별 악플 분포 데이터 (JSON -> Map)
    // 예: { "새벽 (00-06시)": 12, "오전 (06-12시)": 4, ... }
    private Map<String, Integer> timeDistribution;

    // 레드존 정보 (가장 위험한 시간대)
    private String redZoneTimeSlot;      // 예: "새벽 (00-06시)"
    private Integer redZoneCount;         // 레드존 시간대 악플 개수
    private BigDecimal redZonePercentage; // 레드존 비율 (예: 35.29)

    // 메타데이터 (DB 관리용)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

