package com.medi.backend.admin.dto;

import lombok.Data;

@Data
public class PlatformUsageDto {
    private String platform; // "YOUTUBE", "INSTAGRAM" 등
    private Integer userCount;
    private Double percentage; // 비율 (%)
}

