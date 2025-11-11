package com.medi.backend.userdashboard.dto;

import java.util.List;

import lombok.Data;

@Data
public class ChannelFilteringStatisticsDto {
    // 채널 ID
    private Integer channelId;
    
    // 채널명
    private String channelName;
    
    // 총 필터링된 댓글 수
    private Integer totalFilteredCount;
    
    // 해로움 수준별 분포
    private List<UserHarmfulnessLevelDistributionDto> harmfulnessLevelDistribution;
    
    // 카테고리별 분포
    private List<CategoryDistributionForUserDto> categoryDistribution;
}

