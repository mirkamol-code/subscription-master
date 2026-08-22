package com.mirkamolcode.controller;

import com.mirkamolcode.dto.request.SubscriptionRequest;
import com.mirkamolcode.dto.response.SubscriptionResponse;
import com.mirkamolcode.model.CurrencyCode;
import com.mirkamolcode.model.SubscriptionStatus;
import com.mirkamolcode.service.SubscriptionService;
import jakarta.validation.Valid;

import java.math.BigDecimal;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
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

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUBSCRIPTION_READ_OWN', 'SUBSCRIPTION_READ_ALL')")
    public Page<SubscriptionResponse> list(@RequestParam(required = false) SubscriptionStatus status, @RequestParam(required = false) CurrencyCode currency, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice, @ParameterObject @PageableDefault(size = 20, sort = "nextPaymentDate") Pageable pageable) {
        return service.list(status, currency, minPrice, maxPrice, pageable);
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
}
