package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.DuePaymentsResponse;
import com.mirkamolcode.dto.response.UpcomingPaymentsResponse;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.service.AdminPaymentService;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {
    private final AdminPaymentService payments;

    public AdminPaymentController(AdminPaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/due")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public DuePaymentsResponse listDue(@RequestParam LocalDate from,
                                       @RequestParam LocalDate to,
                                       @RequestParam(required = false) String merchant,
                                       @RequestParam(required = false) SubscriptionStatus status) {
        return payments.listDue(from, to, merchant, status);
    }

    @GetMapping("/upcoming")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public UpcomingPaymentsResponse listUpcoming() {
        return payments.listUpcoming();
    }
}
