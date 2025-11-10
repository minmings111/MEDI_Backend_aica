package com.medi.backend.admin.dto;

import lombok.Data;

@Data
public class PlanDistributionDto {
    private Integer planId;
    private String planName;
    private Integer subscriberCount;
    private Double percentage; // 비율 (%)
}

