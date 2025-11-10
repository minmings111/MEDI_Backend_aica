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

    public AdminServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
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
    public Integer getTotalFilteringCount() {
        return adminMapper.getTotalFilteringCount();
    }

    @Override
    @Transactional(readOnly = true)
    public MonthOverMonthDeltaDto getMonthOverMonthDelta() {
        return adminMapper.getMonthOverMonthDelta();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserTrendPointDto> getUserTrendByDateRange(LocalDate from, LocalDate to) {
        // 기본값 설정: from이 null이면 최근 30일
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        // 기본값 설정: to가 null이면 오늘
        if (to == null) {
            to = LocalDate.now();
        }
        // 날짜 범위 검증: from이 to보다 미래일 수 없음
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이전이어야 합니다.");
        }
        return adminMapper.getUserTrendByDateRange(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDistributionDto> getPlanDistribution() {
        List<PlanDistributionDto> distribution = adminMapper.getPlanDistribution();
        
        // 전체 구독자 수 계산하여 비율 계산
        int totalSubscribers = distribution.stream()
            .mapToInt(PlanDistributionDto::getSubscriberCount)
            .sum();
        
        if (totalSubscribers > 0) {
            distribution.forEach(item -> {
                double percentage = (item.getSubscriberCount().doubleValue() / totalSubscribers) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0); // 소수점 2자리
            });
        }
        
        return distribution;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformUsageDto> getPlatformUsageDistribution() {
        List<PlatformUsageDto> distribution = adminMapper.getPlatformUsageDistribution();
        
        // 전체 사용자 수 계산하여 비율 계산
        int totalUsers = distribution.stream()
            .mapToInt(PlatformUsageDto::getUserCount)
            .sum();
        
        if (totalUsers > 0) {
            distribution.forEach(item -> {
                double percentage = (item.getUserCount().doubleValue() / totalUsers) * 100.0;
                item.setPercentage(Math.round(percentage * 100.0) / 100.0); // 소수점 2자리
            });
        }
        
        return distribution;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsDto getStatisticsSummary(LocalDate from, LocalDate to) {
        // 날짜 검증은 getUserTrendByDateRange에서 수행됨
        AdminStatisticsDto adminStatisticsDto = new AdminStatisticsDto();
        
        adminStatisticsDto.setTotalUserCount(getTotalUserCount());
        adminStatisticsDto.setActiveSubscriberCount(getActiveSubscriberCount());
        adminStatisticsDto.setTotalFilteringCount(getTotalFilteringCount());
        adminStatisticsDto.setMonthOverMonthDelta(getMonthOverMonthDelta());
        adminStatisticsDto.setUserTrend(getUserTrendByDateRange(from, to));
        adminStatisticsDto.setPlanDistribution(getPlanDistribution());
        adminStatisticsDto.setPlatformUsage(getPlatformUsageDistribution());
        
        return adminStatisticsDto;
    }
}
