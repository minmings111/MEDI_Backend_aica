package com.medi.backend.billing.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UserSubscriptionDto {
    private Integer id;
    private Integer userId;
    private Integer planId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
