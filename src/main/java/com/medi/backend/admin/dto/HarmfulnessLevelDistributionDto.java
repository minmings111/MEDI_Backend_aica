package com.medi.backend.admin.dto;

import lombok.Data;

@Data
public class HarmfulnessLevelDistributionDto {
    private String harmfulnessLevel; // LOW, MEDIUM, HIGH
    private Integer count;
    private Double percentage; // 비율 (%)
}

