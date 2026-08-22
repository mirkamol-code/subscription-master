package com.mirkamolcode.controller;

import com.mirkamolcode.dto.AdminUsage;
import com.mirkamolcode.service.StatisticsService;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/statistics")
public class AdminController {
    private final StatisticsService service;

    public AdminController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/service-usage")
    @PreAuthorize("hasAuthority('STATISTICS_READ_ALL')")
    public List<AdminUsage> usage() {
        return service.serviceUsage();
    }
}
