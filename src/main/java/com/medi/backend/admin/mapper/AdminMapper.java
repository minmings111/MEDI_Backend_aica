package com.medi.backend.admin.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;

@Mapper
public interface AdminMapper {

    // ADM-01: 총 사용자 수
    Integer getTotalUserCount();

    // ADM-02: 활성 사용자 수 (현재 구독 중인 사용자)
    Integer getActiveSubscriberCount();

    // ADM-03: 총 필터링 수 (모든 등록된 채널의 누적 필터링 댓글 수)
    Integer getTotalFilteringCount();

    // ADM-04: 전월 대비 증감률
    MonthOverMonthDeltaDto getMonthOverMonthDelta();

    // ADM-05: 사용자 변화 추이 그래프 (기간 선택)
    List<UserTrendPointDto> getUserTrendByDateRange(
        @Param("from") LocalDate from, 
        @Param("to") LocalDate to
    );

    // ADM-06: 요금제 구독 분포
    List<PlanDistributionDto> getPlanDistribution();

    // ADM-07: 플랫폼 사용 분포
    List<PlatformUsageDto> getPlatformUsageDistribution();

}
