package com.medi.backend.userdashboard.dto;

import java.util.List;

import lombok.Data;

@Data
public class VideoFilteringStatisticsDto {
    // 비디오 ID
    private Integer videoId;
    
    // 비디오 제목
    private String videoTitle;
    
    // 총 필터링된 댓글 수
    private Integer totalFilteredCount;
    
    // 해로움 수준별 분포
    private List<UserHarmfulnessLevelDistributionDto> harmfulnessLevelDistribution;
    
    // 카테고리별 분포
    private List<CategoryDistributionForUserDto> categoryDistribution;
}

