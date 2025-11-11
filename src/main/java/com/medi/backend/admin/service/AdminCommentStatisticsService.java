package com.medi.backend.admin.service;

import com.medi.backend.admin.dto.FilteringStatisticsDto;

public interface AdminCommentStatisticsService {
    Integer getTotalFilteringCount();
    FilteringStatisticsDto getFilteringStatistics();
}
