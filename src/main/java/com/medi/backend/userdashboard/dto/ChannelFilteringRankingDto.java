package com.medi.backend.userdashboard.dto;

import lombok.Data;

@Data
public class ChannelFilteringRankingDto {
    private Integer channelId;
    private String channelName;
    private Integer filteredCount;
    private Integer rank;
}

