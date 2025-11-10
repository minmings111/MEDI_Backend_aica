package com.medi.backend.admin.dto;

import lombok.Data;

@Data
public class DetectionSourceDistributionDto {
    private String detectionSource; // AI_MODEL, USER_KEYWORD, USER_CONTEXT
    private Integer count;
    private Double percentage; // 비율 (%)
}

