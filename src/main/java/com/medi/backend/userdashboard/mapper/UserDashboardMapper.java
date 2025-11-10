package com.medi.backend.userdashboard.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.userdashboard.dto.CategoryDistributionDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringRankingDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.DetectionSourceDistributionDto;
import com.medi.backend.userdashboard.dto.FilteringTrendPointDto;
import com.medi.backend.userdashboard.dto.HarmfulnessLevelDistributionDto;
import com.medi.backend.userdashboard.dto.UserDashboardSummaryDto;
import com.medi.backend.userdashboard.dto.VideoFilteringRankingDto;
import com.medi.backend.userdashboard.dto.VideoFilteringStatisticsDto;

@Mapper
public interface UserDashboardMapper {

    // UDB-01: 사용자 대시보드 요약 통계
    UserDashboardSummaryDto getDashboardSummary(@Param("userId") Integer userId);

    // UDB-02: 총 필터링 수 (사용자별)
    Integer getTotalFilteringCountByUserId(@Param("userId") Integer userId);

    // UDB-03: 최근 7일 필터링 수
    Integer getLast7DaysFilteringCount(@Param("userId") Integer userId);

    // UDB-04: 이번 달 필터링 수
    Integer getThisMonthFilteringCount(@Param("userId") Integer userId);

    // UDB-05: 지난 달 필터링 수
    Integer getLastMonthFilteringCount(@Param("userId") Integer userId);

    // UDB-06: 해로움 수준별 분포 (사용자별)
    List<HarmfulnessLevelDistributionDto> getHarmfulnessLevelDistributionByUserId(@Param("userId") Integer userId);

    // UDB-07: 탐지 소스별 분포 (사용자별)
    List<DetectionSourceDistributionDto> getDetectionSourceDistributionByUserId(@Param("userId") Integer userId);

    // UDB-08: 카테고리별 분포 (사용자별)
    List<CategoryDistributionDto> getCategoryDistributionByUserId(@Param("userId") Integer userId);

    // UDB-09: 기간별 필터링 추이
    List<FilteringTrendPointDto> getFilteringTrendByDateRange(
        @Param("userId") Integer userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    // UDB-10: 채널별 필터링 통계
    ChannelFilteringStatisticsDto getChannelFilteringStatistics(
        @Param("userId") Integer userId,
        @Param("channelId") Integer channelId
    );

    // UDB-11: 채널별 해로움 수준 분포
    List<HarmfulnessLevelDistributionDto> getChannelHarmfulnessLevelDistribution(
        @Param("userId") Integer userId,
        @Param("channelId") Integer channelId
    );

    // UDB-12: 채널별 카테고리 분포
    List<CategoryDistributionDto> getChannelCategoryDistribution(
        @Param("userId") Integer userId,
        @Param("channelId") Integer channelId
    );

    // UDB-13: 비디오별 필터링 통계
    VideoFilteringStatisticsDto getVideoFilteringStatistics(
        @Param("userId") Integer userId,
        @Param("videoId") Integer videoId
    );

    // UDB-14: 비디오별 해로움 수준 분포
    List<HarmfulnessLevelDistributionDto> getVideoHarmfulnessLevelDistribution(
        @Param("userId") Integer userId,
        @Param("videoId") Integer videoId
    );

    // UDB-15: 비디오별 카테고리 분포
    List<CategoryDistributionDto> getVideoCategoryDistribution(
        @Param("userId") Integer userId,
        @Param("videoId") Integer videoId
    );

    // UDB-16: 채널별 필터링 수 랭킹 (TOP N)
    List<ChannelFilteringRankingDto> getChannelFilteringRanking(
        @Param("userId") Integer userId,
        @Param("limit") Integer limit
    );

    // UDB-17: 비디오별 필터링 수 랭킹 (TOP N)
    List<VideoFilteringRankingDto> getVideoFilteringRanking(
        @Param("userId") Integer userId,
        @Param("limit") Integer limit
    );
}
