package com.medi.backend.admin.dto;

import lombok.Data;

@Data
public class MonthOverMonthDeltaDto {

    private Integer thisMonthUserCount;
    private Integer lastMonthUserCount;
    private Integer thisMonthFilteringCount;
    private Integer lastMonthFilteringCount;

    private Double userGrowthRate; // 사용자 증감률 (%)
    private Double filteringGrowthRate; // 필터링 수 증감률 (%)
}

