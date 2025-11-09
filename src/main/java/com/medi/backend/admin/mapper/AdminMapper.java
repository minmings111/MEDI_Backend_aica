package com.medi.backend.admin.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AdminMapper {

    Integer getTotalUserCount();
    Integer getActiveSubscriberCount();

    // Map<String, Integer> getPlatformUsageCounts();

    List<Map<Integer, Integer>> getPlanDistribution();

    List<Map<String, Object>> getMonthlyUserTrend(LocalDate from, LocalDate to);
    Map<String, Double> getMonthOverMonthDelta();

}