package com.medi.backend.admin.service;

import java.time.LocalDate;
import java.util.List;

import com.medi.backend.admin.dto.AdminStatisticsDto;
import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;

public interface AdminService {

    // ADM-01: 총 사용자 수
    Integer getTotalUserCount();

    // ADM-02: 활성 사용자 수
    Integer getActiveSubscriberCount();

    // ADM-03: 총 필터링 수
    Integer getTotalFilteringCount();

    // ADM-04: 전월 대비 증감률
    MonthOverMonthDeltaDto getMonthOverMonthDelta();

    // ADM-05: 사용자 변화 추이 그래프
    List<UserTrendPointDto> getUserTrendByDateRange(LocalDate from, LocalDate to);

    // ADM-06: 요금제 구독 분포
    List<PlanDistributionDto> getPlanDistribution();

    // ADM-07: 플랫폼 사용 분포
    List<PlatformUsageDto> getPlatformUsageDistribution();

    // 통합 통계 조회 (선택적)
    AdminStatisticsDto getStatisticsSummary(LocalDate from, LocalDate to);
}
