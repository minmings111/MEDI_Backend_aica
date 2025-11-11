package com.medi.backend.admin.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;
import com.medi.backend.admin.dto.HarmfulnessLevelDistributionDto;
import com.medi.backend.admin.dto.DetectionSourceDistributionDto;
import com.medi.backend.admin.dto.CategoryDistributionDto;

@Mapper
public interface AdminMapper {

    // ADM-01: total user count
    Integer getTotalUserCount();

    // ADM-02: active subscriber count (currently subscribed users)
    Integer getActiveSubscriberCount();

    // ADM-03: total filtering count
    Integer getTotalFilteringCount();

    // ADM-04: detailed filtering statistics distribution
    List<HarmfulnessLevelDistributionDto> getHarmfulnessLevelDistribution();
    List<DetectionSourceDistributionDto> getDetectionSourceDistribution();
    List<CategoryDistributionDto> getCategoryDistribution();

    // ADM-05: month over month delta
    MonthOverMonthDeltaDto getMonthOverMonthDelta();

    // ADM-06: user trend graph (date range selection)
    List<UserTrendPointDto> getUserTrendByDateRange(
        @Param("from") LocalDate from, 
        @Param("to") LocalDate to
    );

    // ADM-07: plan distribution
    List<PlanDistributionDto> getPlanDistribution();

    // ADM-08: platform usage distribution
    List<PlatformUsageDto> getPlatformUsageDistribution();

}
