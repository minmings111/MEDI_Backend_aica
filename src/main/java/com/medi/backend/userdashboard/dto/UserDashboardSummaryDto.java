package com.medi.backend.userdashboard.dto;

import lombok.Data;

@Data
public class UserDashboardSummaryDto {
    // 연결된 채널 수
    private Integer totalChannelCount;
    
    // 총 비디오 수
    private Integer totalVideoCount;
    
    // 총 필터링된 댓글 수
    private Integer totalFilteredCount;
    
    // 최근 7일 필터링 수
    private Integer last7DaysFilteredCount;
    
    // 이번 달 필터링 수
    private Integer thisMonthFilteredCount;
    
    // 지난 달 필터링 수
    private Integer lastMonthFilteredCount;
    
    // 월별 증감률 (%)
    private Double monthOverMonthGrowthRate;
}

