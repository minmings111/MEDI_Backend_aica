package com.medi.backend.userdashboard.dto;

import java.util.List;

import lombok.Data;

@Data
public class UserFilteringStatisticsDto {
    // 총 필터링된 댓글 수
    private Integer totalFilteredCount;
    
    // 해로움 수준별 분포 (LOW, MEDIUM, HIGH)
    private List<HarmfulnessLevelDistributionDto> harmfulnessLevelDistribution;
    
    // 필터링 로직별 분포 (AI_MODEL, USER_KEYWORD, USER_CONTEXT)
    private List<DetectionSourceDistributionDto> detectionSourceDistribution;
    
    // 카테고리별 분포 (SPAM, HATE_SPEECH 등)
    private List<CategoryDistributionDto> categoryDistribution;
}

