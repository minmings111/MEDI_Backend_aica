package com.medi.backend.admin.dto;

import java.util.List;

import lombok.Data;

@Data
public class AdminStatisticsDto {
    private Integer totalUserCount;
    private Integer activeSubscriberCount;
    private Integer totalFilteringCount;
    private MonthOverMonthDeltaDto monthOverMonthDelta;
    private List<UserTrendPointDto> userTrend;
    private List<PlanDistributionDto> planDistribution;
    private List<PlatformUsageDto> platformUsage;
}

