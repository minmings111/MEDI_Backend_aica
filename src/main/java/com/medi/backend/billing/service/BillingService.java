package com.medi.backend.billing.service;

import java.util.List;

import com.medi.backend.billing.dto.PaymentMethodDto;
import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.dto.UserSubscriptionDto;
import com.medi.backend.billing.dto.UserSubscriptionFilterDto;

public interface BillingService {

    // SubscriptionPlanDto.java
    public List<SubscriptionPlanDto> getAllPlans();
    public SubscriptionPlanDto getOnePlanById(int id);
    
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto);
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto);
    public int deletePlanById(int id);

    // PaymentMethodDto.java
    public List<PaymentMethodDto> getPaymentMethodsByUserId(int userId);
    public int createPaymentMethod(PaymentMethodDto paymentMethodDto);
    public int deletePaymentMethodByIdAndUserId(int id, int userId);

    public void setAsDefaultPaymentMethod(int id, int userId);

    // UserSubscriptionDto.java
    public UserSubscriptionDto getActiveSubscriptionByUserId(int userId); // for system act 
    public List<UserSubscriptionDto> getSubscriptionHistoryByUserId(int userId); // for history of user
    public List<UserSubscriptionDto> getAllSubscriptions(UserSubscriptionFilterDto userSubscriptionFilterDto); // for admin

    public int createUserSubscription(int userId, int planId);
    public int cancelSubscriptionByIdAndUserId(int subscriptionId, int userId);

    public int changeSubscriptionPlan(int userId, int planId);
    
} 
