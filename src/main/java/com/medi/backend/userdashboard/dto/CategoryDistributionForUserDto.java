package com.medi.backend.userdashboard.dto;

import lombok.Data;

@Data
public class CategoryDistributionForUserDto {
    private String category; // SPAM, HATE_SPEECH 등
    private Integer count;
    private Double percentage; // 비율 (%)
}

