package com.medi.backend.billing.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.medi.backend.billing.dto.SubscriptionPlanDto;

@Mapper
public interface BillingMapper {

    public List<SubscriptionPlanDto> getAllPlans();
    public SubscriptionPlanDto getOnePlanById(int id);
    
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto);
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto);
    public int deletePlanById(int id);

}
