package com.medi.backend.billing.service;

import java.util.List;

import com.medi.backend.billing.dto.SubscriptionPlanDto;

public interface BillingService {

    public List<SubscriptionPlanDto> getAllPlans();
    public SubscriptionPlanDto getOnePlanById(int id);
    
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto);
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto);
    public int deletePlanById(int id);
    
} 
