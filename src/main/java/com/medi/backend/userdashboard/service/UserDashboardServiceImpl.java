package com.medi.backend.userdashboard.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.userdashboard.dto.CategoryDistributionForUserDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringRankingDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.UserDetectionSourceDistributionDto;
import com.medi.backend.userdashboard.dto.FilteringTrendPointDto;
import com.medi.backend.userdashboard.dto.UserHarmfulnessLevelDistributionDto;
import com.medi.backend.userdashboard.dto.UserDashboardSummaryDto;
import com.medi.backend.userdashboard.dto.UserFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.VideoFilteringRankingDto;
import com.medi.backend.userdashboard.dto.VideoFilteringStatisticsDto;
import com.medi.backend.userdashboard.mapper.UserDashboardMapper;
import com.medi.backend.youtube.dto.YoutubeChannelDto;
import com.medi.backend.youtube.dto.YoutubeVideoDto;
import com.medi.backend.youtube.service.ChannelService;
import com.medi.backend.youtube.service.VideoService;

@Service
public class UserDashboardServiceImpl implements UserDashboardService {

    private final UserDashboardMapper dashboardMapper;
    private final ChannelService channelService;
    private final VideoService videoService;

    public UserDashboardServiceImpl(UserDashboardMapper dashboardMapper,
                                    ChannelService channelService,
                                    VideoService videoService) {
        this.dashboardMapper = dashboardMapper;
        this.channelService = channelService;
        this.videoService = videoService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDashboardSummaryDto getDashboardSummary(Integer userId) {
        return dashboardMapper.getDashboardSummary(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserFilteringStatisticsDto getFilteringStatistics(Integer userId) {
        UserFilteringStatisticsDto statistics = new UserFilteringStatisticsDto();

        Integer totalCount = dashboardMapper.getTotalFilteringCountByUserId(userId);
        statistics.setTotalFilteredCount(totalCount);

        List<UserHarmfulnessLevelDistributionDto> harmfulnessDistribution =
            dashboardMapper.getHarmfulnessLevelDistributionByUserId(userId);
        calculatePercentageForHarmfulness(harmfulnessDistribution, totalCount);
        statistics.setHarmfulnessLevelDistribution(harmfulnessDistribution);

        List<UserDetectionSourceDistributionDto> detectionSourceDistribution =
            dashboardMapper.getDetectionSourceDistributionByUserId(userId);
        calculatePercentageForDetectionSource(detectionSourceDistribution, totalCount);
        statistics.setDetectionSourceDistribution(detectionSourceDistribution);

        List<CategoryDistributionForUserDto> categoryDistribution =
            dashboardMapper.getCategoryDistributionByUserId(userId);
        calculatePercentageForCategory(categoryDistribution, totalCount);
        statistics.setCategoryDistribution(categoryDistribution);

        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilteringTrendPointDto> getFilteringTrend(Integer userId, LocalDate from, LocalDate to, Integer channelId, Integer videoId) {
        // 기본값 설정: from이 null이면 30일 전, to가 null이면 오늘
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDate.now();
        }

        // 날짜 검증
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다.");
        }

        // 날짜 범위 제한: 최대 1년
        if (from.isBefore(LocalDate.now().minusYears(1))) {
            throw new IllegalArgumentException("조회 기간은 최대 1년까지 가능합니다.");
        }

        return dashboardMapper.getFilteringTrendByDateRange(userId, from, to, channelId, videoId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelFilteringStatisticsDto getChannelFilteringStatistics(Integer userId, Integer channelId) {
        // 입력값 검증
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 채널 ID입니다.");
        }

        // 채널 소유권 검증
        YoutubeChannelDto channel = channelService.getOneChannelByIdAndUserId(channelId, userId);
        if (channel == null) {
            throw new IllegalArgumentException("해당 채널에 대한 접근 권한이 없습니다.");
        }

        ChannelFilteringStatisticsDto statistics = dashboardMapper.getChannelFilteringStatistics(userId, channelId);
        
        if (statistics == null || statistics.getTotalFilteredCount() == null || statistics.getTotalFilteredCount() == 0) {
            // 데이터가 없는 경우 빈 통계 객체 반환
            statistics = new ChannelFilteringStatisticsDto();
            statistics.setChannelId(channelId);
            statistics.setChannelName(channel.getChannelName());
            statistics.setTotalFilteredCount(0);
            return statistics;
        }

        Integer totalCount = statistics.getTotalFilteredCount();

        List<UserHarmfulnessLevelDistributionDto> harmfulnessDistribution =
            dashboardMapper.getChannelHarmfulnessLevelDistribution(userId, channelId);
        calculatePercentageForHarmfulness(harmfulnessDistribution, totalCount);
        statistics.setHarmfulnessLevelDistribution(harmfulnessDistribution);

        List<CategoryDistributionForUserDto> categoryDistribution =
            dashboardMapper.getChannelCategoryDistribution(userId, channelId);
        calculatePercentageForCategory(categoryDistribution, totalCount);
        statistics.setCategoryDistribution(categoryDistribution);

        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public VideoFilteringStatisticsDto getVideoFilteringStatistics(Integer userId, Integer videoId) {
        // 입력값 검증
        if (videoId == null || videoId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 비디오 ID입니다.");
        }

        // 비디오 소유권 검증
        YoutubeVideoDto video = videoService.getVideoByIdAndUserId(videoId, userId);
        if (video == null) {
            throw new IllegalArgumentException("해당 비디오에 대한 접근 권한이 없습니다.");
        }

        VideoFilteringStatisticsDto statistics = dashboardMapper.getVideoFilteringStatistics(userId, videoId);
        
        if (statistics == null || statistics.getTotalFilteredCount() == null || statistics.getTotalFilteredCount() == 0) {
            // 데이터가 없는 경우 빈 통계 객체 반환
            statistics = new VideoFilteringStatisticsDto();
            statistics.setVideoId(videoId);
            statistics.setVideoTitle(video.getTitle());
            statistics.setTotalFilteredCount(0);
            return statistics;
        }

        Integer totalCount = statistics.getTotalFilteredCount();

        List<UserHarmfulnessLevelDistributionDto> harmfulnessDistribution =
            dashboardMapper.getVideoHarmfulnessLevelDistribution(userId, videoId);
        calculatePercentageForHarmfulness(harmfulnessDistribution, totalCount);
        statistics.setHarmfulnessLevelDistribution(harmfulnessDistribution);

        List<CategoryDistributionForUserDto> categoryDistribution =
            dashboardMapper.getVideoCategoryDistribution(userId, videoId);
        calculatePercentageForCategory(categoryDistribution, totalCount);
        statistics.setCategoryDistribution(categoryDistribution);

        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelFilteringRankingDto> getChannelFilteringRanking(Integer userId, Integer limit) {
        // 기본값: limit이 null이면 10개
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        // 최대값 제한: 100개를 초과할 수 없음
        if (limit > 100) {
            limit = 100;
        }
        return dashboardMapper.getChannelFilteringRanking(userId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoFilteringRankingDto> getVideoFilteringRanking(Integer userId, Integer limit) {
        // 기본값: limit이 null이면 10개
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        // 최대값 제한: 100개를 초과할 수 없음
        if (limit > 100) {
            limit = 100;
        }
        return dashboardMapper.getVideoFilteringRanking(userId, limit);
    }

    private void calculatePercentageForHarmfulness(
            List<UserHarmfulnessLevelDistributionDto> distribution, Integer totalCount) {
        if (totalCount == null || totalCount == 0) {
            return;
        }
        distribution.forEach(item -> {
            if (item.getCount() != null && item.getCount() > 0) {
                double percentage = (item.getCount().doubleValue() / totalCount) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0);
            }
        });
    }

    private void calculatePercentageForDetectionSource(
            List<UserDetectionSourceDistributionDto> distribution, Integer totalCount) {
        if (totalCount == null || totalCount == 0) {
            return;
        }
        distribution.forEach(item -> {
            if (item.getCount() != null && item.getCount() > 0) {
                double percentage = (item.getCount().doubleValue() / totalCount) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0);
            }
        });
    }

    private void calculatePercentageForCategory(
            List<CategoryDistributionForUserDto> distribution, Integer totalCount) {
        if (totalCount == null || totalCount == 0) {
            return;
        }
        distribution.forEach(item -> {
            if (item.getCount() != null && item.getCount() > 0) {
                double percentage = (item.getCount().doubleValue() / totalCount) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0);
            }
        });
    }
}
