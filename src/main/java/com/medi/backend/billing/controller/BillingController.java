package com.medi.backend.billing.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.billing.dto.PaymentMethodDto;
import com.medi.backend.billing.dto.SubscriptionChange;
import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.dto.UserSubscriptionDto;
import com.medi.backend.billing.dto.UserSubscriptionFilterDto;
import com.medi.backend.billing.service.BillingService;
import com.medi.backend.global.util.AuthUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private BillingService billingService;
    private AuthUtil authUtil;

    public BillingController(BillingService billingService, AuthUtil authUtil){
        this.billingService = billingService;
        this.authUtil = authUtil;
    }

    // SubscriptionPlanDto.java
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDto>> getAllPlans() {

        return new ResponseEntity<List<SubscriptionPlanDto>>(billingService.getAllPlans(), HttpStatus.OK);
        // return ResponseEntity.ok(billingService.getAllPlans());
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlanDto> getOnePlanById(@PathVariable("id") Integer id) {
        // return new ResponseEntity<>(billingService.getOnePlanById(id), HttpStatus.OK);

        SubscriptionPlanDto subscriptionPlanDto = billingService.getOnePlanById(id);

        if (subscriptionPlanDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscriptionPlanDto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/plans")
    public ResponseEntity<String> createPlan(@RequestBody SubscriptionPlanDto subscriptionPlanDto) {
        int createCount = billingService.createPlan(subscriptionPlanDto);
        
        // return createCount == 1 ?
        //     new ResponseEntity<String>("success", HttpStatus.OK)
        //     :
        //     new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

        if (createCount == 1) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Subscription plan created successfully");
        } else if (createCount == -1) {
            // duplicated error(-1): "409 Conflict"
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Subscription plan already exists.");
        } else {
            // other error(0 or unexpected value): "500 Internal Server Error"
            return new ResponseEntity<>("Failed to create subscription plan due to unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/plans/{id}")
    public ResponseEntity<String> updatePlan(@PathVariable("id") Integer id, @RequestBody SubscriptionPlanDto subscriptionPlanDto) {
        subscriptionPlanDto.setId(id);
        int updateCount = billingService.updatePlan(subscriptionPlanDto);
        
        if (updateCount == 1) {
            return ResponseEntity.ok("Update Success"); 
        } 
        // 500 server error -> 404 Not Found
        return new ResponseEntity<>("Update Failed: Plan not found", HttpStatus.NOT_FOUND);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<String> deletePlanById(@PathVariable("id") Integer id){
        int deleteCount = billingService.deletePlanById(id);

        if (deleteCount == 1) {
            return ResponseEntity.ok("Delete Success"); 
        } 
        return new ResponseEntity<>("Delete Failed: Plan not found", HttpStatus.NOT_FOUND);
    }

    
    // PaymentMethodDto.java
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethodsByUserId() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<PaymentMethodDto> paymentMethodDtos = billingService.getPaymentMethodsByUserId(userId);
        return ResponseEntity.ok(paymentMethodDtos);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/payment-methods")
    public ResponseEntity<String> createPaymentMethod(@RequestBody PaymentMethodDto paymentMethodDto) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        paymentMethodDto.setUserId(userId);

        int createPaymentMethodResult = billingService.createPaymentMethod(paymentMethodDto);
        
        if (createPaymentMethodResult == 1) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Payment method created successfully");
        } else if (createPaymentMethodResult == -1) {
            // duplicated error(-1): "409 Conflict"
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment method already exists.");
        } else {
            // other error(0 or unexpected value): "500 Internal Server Error"
            return new ResponseEntity<>("Failed to create payment method due to unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/payment-methods/{id}")
    public ResponseEntity<String> deletePaymentMethodByIdAndUserId(@PathVariable int id){
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        int deleteCount = billingService.deletePaymentMethodByIdAndUserId(id, userId);
        
        if (deleteCount == 1) {
            return ResponseEntity.ok("Delete Success"); 
        }
        return new ResponseEntity<>("Delete Failed: No card found or not your card", HttpStatus.NOT_FOUND);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/payment-methods/{id}/set-default")
    public ResponseEntity<String> setAsDefaultPaymentMethod(@PathVariable int id){
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        try {
            billingService.setAsDefaultPaymentMethod(id, userId);
            return ResponseEntity.ok("Default payment method has been set.");

        } catch (Exception e) {
            return new ResponseEntity<>("Failed to set default method: Card not found or permission denied.", HttpStatus.NOT_FOUND);
        }
    }


    // UserSubscriptionDto.java
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/subscriptions/my-active")
    public ResponseEntity<UserSubscriptionDto> getActiveSubscriptionByUserId() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserSubscriptionDto userSubscriptionDto = billingService.getActiveSubscriptionByUserId(userId);
        
        if (userSubscriptionDto == null) {
            return ResponseEntity.noContent().build();  // 204 No Content
        }
        return ResponseEntity.ok(userSubscriptionDto);  // 200 OK
    }
    
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/subscriptions/my-history")
    public ResponseEntity<List<UserSubscriptionDto>> getSubscriptionHistoryByUserId() {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<UserSubscriptionDto> userSubscriptionDtos = billingService.getSubscriptionHistoryByUserId(userId);
        return ResponseEntity.ok(userSubscriptionDtos);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/subscriptions")
    public ResponseEntity<?> getAllSubscriptions(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "planId",required = false) Integer planId,
        @RequestParam(name = "after", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after
    ) {
        UserSubscriptionFilterDto subscriptionFilterDto = new UserSubscriptionFilterDto();
        subscriptionFilterDto.setStatus(status);
        subscriptionFilterDto.setPlanId(planId);
        subscriptionFilterDto.setStartDateAfter(after);

        List<UserSubscriptionDto> userSubscriptionDtos = billingService.getAllSubscriptions(subscriptionFilterDto);
        return ResponseEntity.ok(userSubscriptionDtos);
    }
    
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscriptions")
    public ResponseEntity<String> createUserSubscription(@RequestBody Map<String, Integer> request) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        int planId = request.get("planId");

        int result = billingService.createUserSubscription(userId, planId);

        if (result == 1) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Subscription created successfully");
        } else if (result == -1) {
            // Duplicate active subscriptions error
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User already has an active subscription.");
        } else {
            // other error
            return new ResponseEntity<>("Failed to create subscription due to unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/subscriptions/{subscriptionId}/cancel")
    public ResponseEntity<String> cancelSubscription(@PathVariable int subscriptionId) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        
        int updateCount = billingService.cancelSubscriptionByIdAndUserId(subscriptionId, userId);
        
        if (updateCount == 1) {
            return ResponseEntity.ok("Subscription cancelled successfully."); 
        }
        // The subscriptionId does not exist, has already been cancelled, or does not belong to user.
        return new ResponseEntity<>("Cancellation failed: Subscription not found or already inactive.", HttpStatus.NOT_FOUND);
    }


    @PreAuthorize("isAuthenticated()")
    @PutMapping("/subscriptions/change-plan")
    public ResponseEntity<String> changeSubscriptionPlan(@RequestBody SubscriptionChange subscriptionChange) {
        Integer userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다");
        }
        
        int result = billingService.changeSubscriptionPlan(userId, subscriptionChange.getNewPlanId());
        
        if (result == -1) {
            return ResponseEntity.ok("No subscription plan.");  // 200 OK
        } else if (result == 0) {
            return ResponseEntity.ok("Already using that plan.");  // 200 OK
        } else if (result == 1) {
            return ResponseEntity.ok("The plan has been successfully changed.");  // 200 OK
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("An error occurred while changing the plan.");
    }



}
