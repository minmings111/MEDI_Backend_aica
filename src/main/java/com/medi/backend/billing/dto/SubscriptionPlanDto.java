package com.medi.backend.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SubscriptionPlanDto {
    private Integer id;
    private String planName;
    private BigDecimal price;
    private Integer channelLimit;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
