package com.medi.backend.billing.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class PaymentMethodDto {

    private Integer id;
    private Integer userId;
    private String pgBillingKey;
    private String cardType;
    private String cardLastFour;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
}
