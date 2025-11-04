package com.medi.backend.billing.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medi.backend.billing.dto.PaymentMethodDto;
import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.dto.UserSubscriptionDto;
import com.medi.backend.billing.dto.UserSubscriptionFilterDto;
import com.medi.backend.billing.mapper.BillingMapper;

@Service
public class BillingServiceImpl implements BillingService{

    private final BillingMapper billingMapper;

    public BillingServiceImpl(BillingMapper billingMapper){
        this.billingMapper = billingMapper;
    }

    // SubscriptionPlanDto.java
    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> getAllPlans() {
        return billingMapper.getAllPlans();
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanDto getOnePlanById(int id) {
        return billingMapper.getOnePlanById(id);
    }

    @Override
    @Transactional
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto) {
        return billingMapper.createPlan(subscriptionPlanDto);
    }

    @Override
    @Transactional
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto) {
        return billingMapper.updatePlan(subscriptionPlanDto) ;
    }

    @Override
    @Transactional
    public int deletePlanById(int id) {
        return billingMapper.deletePlanById(id);
    }

    // PaymentMethodDto.java
    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodDto> getPaymentMethodsByUserId(int userId) {
        return billingMapper.getPaymentMethodsByUserId(userId);
    }

    @Override
    @Transactional
    public int createPaymentMethod(PaymentMethodDto paymentMethodDto) {
        try {
            int createPaymentMethodResult = billingMapper.createPaymentMethod(paymentMethodDto);
            return createPaymentMethodResult; // If successful, return 1

        } catch (DuplicateKeyException e) {
            System.err.println("Duplicate payment method detected: " + e.getMessage());
            return -1; // -1 means a duplicate error.
        }
    }

    @Override
    @Transactional
    public int deletePaymentMethodByIdAndUserId(int id, int userId) {
        return billingMapper.deletePaymentMethodByIdAndUserId(id, userId);
    }

    @Override
    @Transactional
    public void setAsDefaultPaymentMethod(int id, int userId) {
        billingMapper.clearAllDefaultPaymentMethodsByUserId(userId);
        int updateCount = billingMapper.setDefaultMethodById(id, userId);
        
        // If updateCount is 0, it means:
        // (1) The specified ID was not found, or
        // (2) The operation attempted to access a card belonging to another user (user_id mismatch).
        // -> must rollback!!!
        if (updateCount == 0){
            throw new RuntimeException("Setting default method failed: Card not found or permission denied.");
        }

    }

    // UserSubscriptionDto.java
    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionDto getActiveSubscriptionByUserId(int userId) {
        return billingMapper.getActiveSubscriptionByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionDto> getSubscriptionHistoryByUserId(int userId) {
        return billingMapper.getSubscriptionHistoryByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionDto> getAllSubscriptions(UserSubscriptionFilterDto userSubscriptionFilterDto) {
        return billingMapper.getAllSubscriptions(userSubscriptionFilterDto);
    }


    @Override
    @Transactional
    public int createUserSubscription(int userId, int planId) {

        // Is the user subscribed?
        UserSubscriptionDto activeSubscription = billingMapper.getActiveSubscriptionByUserId(userId);
        
        if (activeSubscription != null) {
            return -1; // -1: active subscription duplicate error
        }
    
        // 2. calculate the date automatically by server
        UserSubscriptionDto userSubscriptionDto = new UserSubscriptionDto();
        userSubscriptionDto.setUserId(userId);
        userSubscriptionDto.setPlanId(planId);
        
        LocalDateTime now = LocalDateTime.now();
        userSubscriptionDto.setStartDate(now);
        userSubscriptionDto.setEndDate(now.plusDays(30));  // 1month = 30days


        try {
            int result = billingMapper.createUserSubscription(userSubscriptionDto);
            return result; // 1: success

        } catch (DuplicateKeyException e) {
            // maybe for other error.. if act duplicate error.... like... pgBillingKey
            System.err.println("Database constraint violation during subscription creation: " + e.getMessage()); 
            throw e; // DB error. rollback and 505 error!!!!
        }

    }

    @Override
    @Transactional
    public int cancelSubscriptionByIdAndUserId(int subscriptionId, int userId) {
        return billingMapper.cancelSubscriptionByIdAndUserId(subscriptionId, userId);
    }
   

    @Override
    @Transactional
    public int changeSubscriptionPlan(int userId, int planId) {
        UserSubscriptionDto userSubscriptionDto = billingMapper.getActiveSubscriptionByUserId(userId);

        if (userSubscriptionDto == null) {
            return -1; // No subscription plans
        } else if (userSubscriptionDto.getPlanId() == planId) {
            return 0; // Already using that plan
        }
        
        // Cancel existing subscription
        billingMapper.cancelSubscriptionByIdAndUserId(userSubscriptionDto.getId(), userId);
        
        // create new subscriptionDto
        UserSubscriptionDto newSubscriptionDto = new UserSubscriptionDto();
        newSubscriptionDto.setUserId(userId);
        newSubscriptionDto.setPlanId(planId);

        LocalDateTime now = LocalDateTime.now();
        newSubscriptionDto.setStartDate(now);
        newSubscriptionDto.setEndDate(now.plusDays(30)); 
        
        billingMapper.createUserSubscription(newSubscriptionDto);
        return 1;
    }



}
