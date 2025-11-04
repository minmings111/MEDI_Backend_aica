package com.medi.backend.billing.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserSubscriptionFilterDto {
    private String status;
    private LocalDateTime startDateAfter;
    private Integer planId;
}
