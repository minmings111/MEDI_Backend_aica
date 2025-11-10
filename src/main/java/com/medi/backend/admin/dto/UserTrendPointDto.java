package com.medi.backend.admin.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class UserTrendPointDto {
    private LocalDate date;
    private Integer userCount;
}

