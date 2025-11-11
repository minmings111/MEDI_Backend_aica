package com.medi.backend.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.admin.dto.CategoryDistributionDto;
import com.medi.backend.admin.dto.DetectionSourceDistributionDto;
import com.medi.backend.admin.dto.FilteringStatisticsDto;
import com.medi.backend.admin.dto.HarmfulnessLevelDistributionDto;
import com.medi.backend.admin.mapper.AdminMapper;

@Service
public class AdminCommentStatisticsServiceImpl implements AdminCommentStatisticsService {

    private final AdminMapper adminMapper;

    public AdminCommentStatisticsServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalFilteringCount() {
        return adminMapper.getTotalFilteringCount();
    }

    @Override
    @Transactional(readOnly = true)
    public FilteringStatisticsDto getFilteringStatistics() {
        FilteringStatisticsDto statistics = new FilteringStatisticsDto();

        Integer totalCount = adminMapper.getTotalFilteringCount();
        statistics.setTotalFilteredCount(totalCount);

        List<HarmfulnessLevelDistributionDto> harmfulnessDistribution =
            adminMapper.getHarmfulnessLevelDistribution();
        calculatePercentageForHarmfulness(harmfulnessDistribution, totalCount);
        statistics.setHarmfulnessLevelDistribution(harmfulnessDistribution);

        List<DetectionSourceDistributionDto> detectionSourceDistribution =
            adminMapper.getDetectionSourceDistribution();
        calculatePercentageForDetectionSource(detectionSourceDistribution, totalCount);
        statistics.setDetectionSourceDistribution(detectionSourceDistribution);

        List<CategoryDistributionDto> categoryDistribution =
            adminMapper.getCategoryDistribution();
        calculatePercentageForCategory(categoryDistribution, totalCount);
        statistics.setCategoryDistribution(categoryDistribution);

        return statistics;
    }

    private void calculatePercentageForHarmfulness(
            List<HarmfulnessLevelDistributionDto> distribution, Integer totalCount) {
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
            List<DetectionSourceDistributionDto> distribution, Integer totalCount) {
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
            List<CategoryDistributionDto> distribution, Integer totalCount) {
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
