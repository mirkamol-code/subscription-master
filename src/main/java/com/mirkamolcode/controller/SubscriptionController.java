package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.StatusChangeRequest;
import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.StatusChangeResponse;
import com.mirkamolcode.dto.response.BillingEmailResponse;
import com.mirkamolcode.dto.response.CalendarResponse;
import com.mirkamolcode.dto.response.PaymentHistoryResponse;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@Validated
public class SubscriptionController {
    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public Page<SubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) CurrencyCode currency,
            @RequestParam(required = false) SubscriptionCategory category,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) LocalDate nextPaymentDateFrom,
            @RequestParam(required = false) LocalDate nextPaymentDateTo,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean hidden,
            @ParameterObject @PageableDefault(size = 20, sort = "nextPaymentDate") Pageable pageable) {
        return service.list(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice, hidden, pageable);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<SubscriptionResponse> listAll(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) CurrencyCode currency,
            @RequestParam(required = false) SubscriptionCategory category,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) LocalDate nextPaymentDateFrom,
            @RequestParam(required = false) LocalDate nextPaymentDateTo,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean hidden) {
        return service.listAll(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice, hidden);
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<SubscriptionResponse> upcoming(
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int days) {
        return service.upcoming(days);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public CalendarResponse calendar(
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return service.calendar(year, month);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public SubscriptionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse update(@PathVariable Long id, @Valid @RequestBody SubscriptionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_DELETE_OWN', 'SUBSCRIPTION_DELETE_ALL')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return service.changeStatus(id, request);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<StatusChangeResponse> statusHistory(@PathVariable Long id) {
        return service.findStatusHistory(id);
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse hide(@PathVariable Long id) {
        return service.hide(id);
    }

    @PostMapping("/{id}/unhide")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse unhide(@PathVariable Long id) {
        return service.unhide(id);
    }

    @GetMapping("/{id}/emails")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<BillingEmailResponse> emails(@PathVariable Long id) {
        return service.findEmails(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<PaymentHistoryResponse> history(@PathVariable Long id) {
        return service.getHistory(id);
    }
}
