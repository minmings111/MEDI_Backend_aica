package com.medi.backend.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.medi.backend.admin.dto.FilteringStatisticsDto;
import com.medi.backend.admin.service.AdminCommentStatisticsService;

@RestController
@RequestMapping("/api/admin/statistics")
@Tag(name = "Admin Comment Statistics", description = "Admin Comment Statistics API")
public class AdminCommentStatisticsController {

    private final AdminCommentStatisticsService commentStatisticsService;

    public AdminCommentStatisticsController(AdminCommentStatisticsService commentStatisticsService) {
        this.commentStatisticsService = commentStatisticsService;
    }

    // Comment-01: total filtering count
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/total-filtering")
    public ResponseEntity<Integer> getTotalFilteringCount() {
        Integer totalFilteringCount = commentStatisticsService.getTotalFilteringCount();
        return ResponseEntity.ok(totalFilteringCount);
    }

    // Comment-02: detailed filtering statistics
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/filtering-statistics")
    public ResponseEntity<FilteringStatisticsDto> getFilteringStatistics() {
        FilteringStatisticsDto statistics = commentStatisticsService.getFilteringStatistics();
        return ResponseEntity.ok(statistics);
    }
}
