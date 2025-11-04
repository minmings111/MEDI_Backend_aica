package com.medi.backend.billing.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.medi.backend.billing.dto.PaymentMethodDto;
import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.dto.UserSubscriptionDto;
import com.medi.backend.billing.dto.UserSubscriptionFilterDto;

@Mapper
public interface BillingMapper {

    // SubscriptionPlanDto.java
    public List<SubscriptionPlanDto> getAllPlans();
    public SubscriptionPlanDto getOnePlanById(int id);
    
    public int createPlan(SubscriptionPlanDto subscriptionPlanDto);
    public int updatePlan(SubscriptionPlanDto subscriptionPlanDto);
    public int deletePlanById(int id);

    // PaymentMethodDto.java
    public List<PaymentMethodDto> getPaymentMethodsByUserId(int userId);
    public int createPaymentMethod(PaymentMethodDto paymentMethodDto);
    public int deletePaymentMethodByIdAndUserId(@Param("id") int id, @Param("userId") int userId);

    public int clearAllDefaultPaymentMethodsByUserId(int userId);
    public int setDefaultMethodById(@Param("id") int id, @Param("userId") int userId);

    // UserSubscriptionDto.java
    public UserSubscriptionDto getActiveSubscriptionByUserId(int userId); // for system act 
    public List<UserSubscriptionDto> getSubscriptionHistoryByUserId(int userId); // for history of user
    public List<UserSubscriptionDto> getAllSubscriptions(UserSubscriptionFilterDto userSubscriptionFilterDto); // for admin

    public int createUserSubscription(UserSubscriptionDto userSubscriptionDto);
    public int cancelSubscriptionByIdAndUserId(@Param("subscriptionId") int subscriptionId, @Param("userId") int userId);

    

}
