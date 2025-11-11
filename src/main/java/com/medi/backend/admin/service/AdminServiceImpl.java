package com.medi.backend.admin.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.admin.dto.AdminStatisticsDto;
import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;
import com.medi.backend.admin.mapper.AdminMapper;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final AdminCommentStatisticsService commentStatisticsService;

    public AdminServiceImpl(AdminMapper adminMapper,
                            AdminCommentStatisticsService commentStatisticsService) {
        this.adminMapper = adminMapper;
        this.commentStatisticsService = commentStatisticsService;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalUserCount() {
        return adminMapper.getTotalUserCount();
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getActiveSubscriberCount() {
        return adminMapper.getActiveSubscriberCount();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthOverMonthDeltaDto getMonthOverMonthDelta() {
        return adminMapper.getMonthOverMonthDelta();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrendPointDto> getUserTrendByDateRange(LocalDate from, LocalDate to) {
        // default value: if from is null, set it to 30 days ago
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        // default value: if to is null, set it to today
        if (to == null) {
            to = LocalDate.now();
        }
        // date range validation: from cannot be after to
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
        }
        // 날짜 범위 제한: 최대 1년
        if (from.isBefore(LocalDate.now().minusYears(1))) {
            throw new IllegalArgumentException("조회 기간은 최대 1년까지 가능합니다.");
        }
        return adminMapper.getUserTrendByDateRange(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDistributionDto> getPlanDistribution() {
        List<PlanDistributionDto> distribution = adminMapper.getPlanDistribution();
        
        // calculate total subscribers count and percentage
        int totalSubscribers = distribution.stream()
            .mapToInt(PlanDistributionDto::getSubscriberCount)
            .sum();
        
        if (totalSubscribers > 0) {
            distribution.forEach(item -> {
                double percentage = (item.getSubscriberCount().doubleValue() / totalSubscribers) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0); // round to 2 decimal places
            });
        }
        
        return distribution;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformUsageDto> getPlatformUsageDistribution() {
        List<PlatformUsageDto> distribution = adminMapper.getPlatformUsageDistribution();
        
        // calculate total users count and percentage
        int totalUsers = distribution.stream()
            .mapToInt(PlatformUsageDto::getUserCount)
            .sum();
        
        if (totalUsers > 0) {
            distribution.forEach(item -> {
                double percentage = (item.getUserCount().doubleValue() / totalUsers) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0); // round to 2 decimal places
            });
        }
        
        return distribution;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsDto getStatisticsSummary(LocalDate from, LocalDate to) {
        // date validation is performed in getUserTrendByDateRange
        AdminStatisticsDto adminStatisticsDto = new AdminStatisticsDto();
        
        // total statistics - 01, 02, 03, 05, 06, 07, 08
        adminStatisticsDto.setTotalUserCount(getTotalUserCount());
        adminStatisticsDto.setActiveSubscriberCount(getActiveSubscriberCount());
        adminStatisticsDto.setTotalFilteringCount(commentStatisticsService.getTotalFilteringCount());
        adminStatisticsDto.setMonthOverMonthDelta(getMonthOverMonthDelta());
        adminStatisticsDto.setUserTrend(getUserTrendByDateRange(from, to));
        adminStatisticsDto.setPlanDistribution(getPlanDistribution());
        adminStatisticsDto.setPlatformUsage(getPlatformUsageDistribution());
        
        return adminStatisticsDto;
    }
}
