package com.medi.backend.billing.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.mapper.BillingMapper;

@Service
public class BillingServiceImpl implements BillingService{

    private final BillingMapper billingMapper;

    public BillingServiceImpl(BillingMapper billingMapper){
        this.billingMapper = billingMapper;
    }

    @Override
    public List<SubscriptionPlanDto> getAllPlans() {
        return billingMapper.getAllPlans();
    }

    @Override
    public SubscriptionPlanDto getOnePlanById(int id) {
        return billingMapper.getOnePlanById(id);
    }

    @Override
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto) {
        return billingMapper.createPlan(subscriptionPlanDto);
    }

    @Override
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto) {
        return billingMapper.updatePlan(subscriptionPlanDto) ;
    }

    @Override
    public int deletePlanById(int id) {
        return billingMapper.deletePlanById(id);
    }
    
}
