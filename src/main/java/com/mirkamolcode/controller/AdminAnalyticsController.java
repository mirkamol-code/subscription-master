package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.CategoryAnalyticsResponse;
import com.mirkamolcode.dto.response.MerchantAnalyticsResponse;
import com.mirkamolcode.model.AnalyticsRange;
import com.mirkamolcode.model.SubscriptionCategory;
import com.mirkamolcode.service.AdminAnalyticsService;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {
    private final AdminAnalyticsService analytics;

    public AdminAnalyticsController(AdminAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/merchants")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public List<MerchantAnalyticsResponse> rankMerchants(
            @RequestParam(defaultValue = "LAST_30_DAYS") AnalyticsRange range,
            @RequestParam(required = false) SubscriptionCategory category) {
        return analytics.rankMerchants(range, category);
    }

    @GetMapping("/categories")
    @PreAuthorize("@adminAuthorizationManager.isAdmin(authentication)")
    public List<CategoryAnalyticsResponse> rankCategories(
            @RequestParam(defaultValue = "LAST_30_DAYS") AnalyticsRange range) {
        return analytics.rankCategories(range);
    }
}
