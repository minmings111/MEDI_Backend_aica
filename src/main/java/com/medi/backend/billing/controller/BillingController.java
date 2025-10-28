package com.medi.backend.billing.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medi.backend.billing.dto.SubscriptionPlanDto;
import com.medi.backend.billing.service.BillingService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;




@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private BillingService billingService;

    public BillingController(BillingService billingService){
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanDto>> getAllPlans() {

        return new ResponseEntity<List<SubscriptionPlanDto>>(billingService.getAllPlans(), HttpStatus.OK);
        // return ResponseEntity.ok(billingService.getAllPlans());
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<SubscriptionPlanDto> getOnePlanById(@PathVariable int id) {
        // return new ResponseEntity<>(billingService.getOnePlanById(id), HttpStatus.OK);

        SubscriptionPlanDto subscriptionPlanDto = billingService.getOnePlanById(id);

        if (subscriptionPlanDto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(subscriptionPlanDto);
    }

    @PostMapping("/create")
    public ResponseEntity<String> createPlan(@RequestBody SubscriptionPlanDto subscriptionPlanDto) {
        int createCount = billingService.createPlan(subscriptionPlanDto);
        
        return createCount == 1 ?
            new ResponseEntity<String>("success", HttpStatus.OK)
            :
            new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
        
    }

    @PutMapping("plans/{id}")
    public ResponseEntity<String> updatePlan(@PathVariable int id, @RequestBody SubscriptionPlanDto subscriptionPlanDto) {
        subscriptionPlanDto.setId(id);
        int updateCount = billingService.updatePlan(subscriptionPlanDto);
        
        if (updateCount == 1) {
            return ResponseEntity.ok("Update Success"); 
        } 
        // 500 server error -> 404 Not Found
        return new ResponseEntity<>("Update Failed: Plan not found", HttpStatus.NOT_FOUND);

    }
    
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<String> deletePlanById(@PathVariable int id){
        int deleteCount = billingService.deletePlanById(id);

        if (deleteCount == 1) {
            return ResponseEntity.ok("Delete Success"); 
        } 
        return new ResponseEntity<>("Delete Failed: Plan not found", HttpStatus.NOT_FOUND);
    }

    
}
