package com.medi.backend.admin.service;

import java.time.LocalDate;
import java.util.List;

import com.medi.backend.admin.dto.AdminStatisticsDto;
import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;

public interface AdminService {

    // ADM-01: total user count
    Integer getTotalUserCount();

    // ADM-02: active subscriber count
    Integer getActiveSubscriberCount();

    // ADM-03: month over month delta
    MonthOverMonthDeltaDto getMonthOverMonthDelta();

    // ADM-04: user trend graph (date range selection)
    List<UserTrendPointDto> getUserTrendByDateRange(LocalDate from, LocalDate to);

    // ADM-05: plan distribution
    List<PlanDistributionDto> getPlanDistribution();

    // ADM-06: platform usage distribution
    List<PlatformUsageDto> getPlatformUsageDistribution();

    // ADM-07: total statistics (optional)
    AdminStatisticsDto getStatisticsSummary(LocalDate from, LocalDate to);
}
