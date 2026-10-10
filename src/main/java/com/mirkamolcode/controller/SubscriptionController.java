package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.SubscriptionRequest;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUBSCRIPTION_CREATE')")
    public SubscriptionResponse create(@Valid @RequestBody SubscriptionRequest request) {
        return service.create(request);
    }

    /**
     * Paginated subscription list.
     * TASK-01: ?name= (case-insensitive LIKE)
     * TASK-02: ?category=
     * TASK-05: ?nextPaymentDateFrom= &nextPaymentDateTo=
     */
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
            @ParameterObject @PageableDefault(size = 20, sort = "nextPaymentDate") Pageable pageable) {
        return service.list(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice, pageable);
    }

    /**
     * TASK-03: Unpaged — returns ALL subscriptions for calendar view.
     * GET /subscriptions/all
     */
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
            @RequestParam(required = false) BigDecimal maxPrice) {
        return service.listAll(status, currency, category, name, nextPaymentDateFrom, nextPaymentDateTo, minPrice, maxPrice);
    }

    /**
     * TASK-09: Upcoming renewals feed.
     * GET /subscriptions/upcoming?days=7
     */
    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<SubscriptionResponse> upcoming(
            @RequestParam(defaultValue = "7") @Min(1) @Max(365) int days) {
        return service.upcoming(days);
    }

    /**
     * TASK-04: Calendar endpoint returning per-day grouped data with pre-converted costs in UZS.
     * GET /subscriptions/calendar?year=2026&month=10
     */
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

    /**
     * TASK-08: Pause a subscription (ACTIVE → PAUSED).
     * POST /subscriptions/{id}/pause
     */
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse pause(@PathVariable Long id) {
        return service.pause(id);
    }

    /**
     * TASK-08: Resume a subscription (PAUSED → ACTIVE).
     * POST /subscriptions/{id}/resume
     */
    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_UPDATE_OWN', 'SUBSCRIPTION_UPDATE_ALL')")
    public SubscriptionResponse resume(@PathVariable Long id) {
        return service.resume(id);
    }

    /**
     * TASK-07: Payment history for a subscription.
     * GET /subscriptions/{id}/history
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public List<PaymentHistoryResponse> history(@PathVariable Long id) {
        return service.getHistory(id);
    }
}
