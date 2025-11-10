package com.medi.backend.admin.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.medi.backend.admin.dto.AdminStatisticsDto;
import com.medi.backend.admin.dto.MonthOverMonthDeltaDto;
import com.medi.backend.admin.dto.PlanDistributionDto;
import com.medi.backend.admin.dto.PlatformUsageDto;
import com.medi.backend.admin.dto.UserTrendPointDto;
import com.medi.backend.admin.service.AdminService;

@RestController
@RequestMapping("/api/admin/statistics")
@Tag(name = "Admin Statistics", description = "Admin Statistics API")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ADM-01: total users count
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/total-users")
    public ResponseEntity<Integer> getTotalUserCount() {
        Integer totalUserCount = adminService.getTotalUserCount();
        return ResponseEntity.ok(totalUserCount);
    }

    // ADM-02: active users count
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/active-subscribers")
    public ResponseEntity<Integer> getActiveSubscriberCount() {
        Integer activeSubscriberCount = adminService.getActiveSubscriberCount();
        return ResponseEntity.ok(activeSubscriberCount);
    }

    // ADM-03: total filtering count
    // not yet...
    // @PreAuthorize("hasRole('ADMIN')")
    // @GetMapping("/total-filtering")
    // public ResponseEntity<Integer> getTotalFilteringCount() {
    //     Integer totalFilteringCount = adminService.getTotalFilteringCount();
    //     return ResponseEntity.ok(totalFilteringCount);
    // }

    // ADM-04: month over month delta
    // userGrowRate - 100%, filteringCountGrowRate - 100% and raw data
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/month-over-month-delta")
    public ResponseEntity<MonthOverMonthDeltaDto> getMonthOverMonthDelta() {
        MonthOverMonthDeltaDto delta = adminService.getMonthOverMonthDelta();
        return ResponseEntity.ok(delta);
    }

    // ADM-05: user trend graph 
    // (date range required, default: 1 month's day by day data)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user-trend")
    public ResponseEntity<List<UserTrendPointDto>> getUserTrend(
        @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<UserTrendPointDto> userTrend = adminService.getUserTrendByDateRange(from, to);
        return ResponseEntity.ok(userTrend);
    }

    // ADM-06: plan distribution
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/plan-distribution")
    public ResponseEntity<List<PlanDistributionDto>> getPlanDistribution() {
        List<PlanDistributionDto> planDistribution = adminService.getPlanDistribution();
        return ResponseEntity.ok(planDistribution);
    }

    // ADM-07: 플랫폼 사용 분포
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/platform-usage")
    public ResponseEntity<List<PlatformUsageDto>> getPlatformUsageDistribution() {
        List<PlatformUsageDto> platformUsage = adminService.getPlatformUsageDistribution();
        return ResponseEntity.ok(platformUsage);
    }

    // 통합 통계 조회 (모든 통계를 한 번에)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<AdminStatisticsDto> getStatisticsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AdminStatisticsDto adminStatisticsDto = adminService.getStatisticsSummary(from, to);
        return ResponseEntity.ok(adminStatisticsDto);
    }
}
