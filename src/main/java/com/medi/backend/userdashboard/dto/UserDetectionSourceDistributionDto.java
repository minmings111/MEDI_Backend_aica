package com.medi.backend.userdashboard.dto;

import lombok.Data;

@Data
public class UserDetectionSourceDistributionDto {
    private String detectionSource; // AI_MODEL, USER_KEYWORD, USER_CONTEXT
    private Integer count;
    private Double percentage; // 비율 (%)
}

