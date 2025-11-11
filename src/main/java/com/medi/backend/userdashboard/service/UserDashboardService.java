package com.medi.backend.userdashboard.service;

import java.time.LocalDate;
import java.util.List;

import com.medi.backend.userdashboard.dto.ChannelFilteringRankingDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.UserDashboardSummaryDto;
import com.medi.backend.userdashboard.dto.UserFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.VideoFilteringRankingDto;
import com.medi.backend.userdashboard.dto.VideoFilteringStatisticsDto;

public interface UserDashboardService {

    // UDB-01: 사용자 대시보드 요약 통계
    UserDashboardSummaryDto getDashboardSummary(Integer userId);

    // UDB-02: 전체 필터링 상세 통계
    UserFilteringStatisticsDto getFilteringStatistics(Integer userId);

    // UDB-03: 기간별 필터링 추이
    List<com.medi.backend.userdashboard.dto.FilteringTrendPointDto> getFilteringTrend(
        Integer userId, LocalDate from, LocalDate to
    );

    // UDB-04: 채널별 필터링 통계
    ChannelFilteringStatisticsDto getChannelFilteringStatistics(Integer userId, Integer channelId);

    // UDB-05: 비디오별 필터링 통계
    VideoFilteringStatisticsDto getVideoFilteringStatistics(Integer userId, Integer videoId);

    // UDB-06: 채널별 필터링 수 랭킹
    List<ChannelFilteringRankingDto> getChannelFilteringRanking(Integer userId, Integer limit);

    // UDB-07: 비디오별 필터링 수 랭킹
    List<VideoFilteringRankingDto> getVideoFilteringRanking(Integer userId, Integer limit);
}
