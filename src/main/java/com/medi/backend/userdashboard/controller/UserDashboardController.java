package com.medi.backend.userdashboard.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.medi.backend.global.util.AuthUtil;
import com.medi.backend.userdashboard.dto.ChannelFilteringRankingDto;
import com.medi.backend.userdashboard.dto.ChannelFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.UserDashboardSummaryDto;
import com.medi.backend.userdashboard.dto.UserFilteringStatisticsDto;
import com.medi.backend.userdashboard.dto.VideoFilteringRankingDto;
import com.medi.backend.userdashboard.dto.VideoFilteringStatisticsDto;
import com.medi.backend.userdashboard.service.UserDashboardService;

@RestController
@RequestMapping("/api/user/dashboard")
@Tag(name = "User Dashboard", description = "User Dashboard Statistics API")
public class UserDashboardController {

    private final UserDashboardService dashboardService;
    private final AuthUtil authUtil;

    public UserDashboardController(UserDashboardService dashboardService, AuthUtil authUtil) {
        this.dashboardService = dashboardService;
        this.authUtil = authUtil;
    }

    // UDB-01: 사용자 대시보드 요약 통계
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public ResponseEntity<UserDashboardSummaryDto> getDashboardSummary() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDashboardSummaryDto summary = dashboardService.getDashboardSummary(userId);
        return ResponseEntity.ok(summary);
    }

    // UDB-02: 전체 필터링 상세 통계
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/filtering-statistics")
    public ResponseEntity<UserFilteringStatisticsDto> getFilteringStatistics() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserFilteringStatisticsDto statistics = dashboardService.getFilteringStatistics(userId);
        return ResponseEntity.ok(statistics);
    }

    // UDB-03: 기간별 필터링 추이
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/filtering-trend")
    public ResponseEntity<List<com.medi.backend.userdashboard.dto.FilteringTrendPointDto>> getFilteringTrend(
        @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(name = "channelId", required = false) Integer channelId,
        @RequestParam(name = "videoId", required = false) Integer videoId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<com.medi.backend.userdashboard.dto.FilteringTrendPointDto> trend =
            dashboardService.getFilteringTrend(userId, from, to, channelId, videoId);
        return ResponseEntity.ok(trend);
    }

    // UDB-04: 채널별 필터링 통계
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/channels/{channelId}/filtering-statistics")
    public ResponseEntity<ChannelFilteringStatisticsDto> getChannelFilteringStatistics(
        @PathVariable("channelId") Integer channelId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChannelFilteringStatisticsDto statistics =
            dashboardService.getChannelFilteringStatistics(userId, channelId);
        return ResponseEntity.ok(statistics);
    }

    // UDB-05: 비디오별 필터링 통계
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/videos/{videoId}/filtering-statistics")
    public ResponseEntity<VideoFilteringStatisticsDto> getVideoFilteringStatistics(
        @PathVariable("videoId") Integer videoId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        VideoFilteringStatisticsDto statistics =
            dashboardService.getVideoFilteringStatistics(userId, videoId);
        return ResponseEntity.ok(statistics);
    }

    // UDB-06: 채널별 필터링 수 랭킹
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/channels/filtering-ranking")
    public ResponseEntity<List<ChannelFilteringRankingDto>> getChannelFilteringRanking(
        @RequestParam(name = "limit", required = false) Integer limit) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ChannelFilteringRankingDto> ranking =
            dashboardService.getChannelFilteringRanking(userId, limit);
        return ResponseEntity.ok(ranking);
    }

    // UDB-07: 비디오별 필터링 수 랭킹
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/videos/filtering-ranking")
    public ResponseEntity<List<VideoFilteringRankingDto>> getVideoFilteringRanking(
        @RequestParam(name = "limit", required = false) Integer limit) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<VideoFilteringRankingDto> ranking =
            dashboardService.getVideoFilteringRanking(userId, limit);
        return ResponseEntity.ok(ranking);
    }
}
