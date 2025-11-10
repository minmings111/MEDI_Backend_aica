package com.medi.backend.userdashboard.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class FilteringTrendPointDto {
    private LocalDate date;
    private Integer filteredCount;
}

