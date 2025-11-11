package com.medi.backend.userdashboard.dto;

import lombok.Data;

@Data
public class VideoFilteringRankingDto {
    private Integer videoId;
    private String videoTitle;
    private Integer filteredCount;
    private Integer rank;
}

