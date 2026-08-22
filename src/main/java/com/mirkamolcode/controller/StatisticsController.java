package com.mirkamolcode.controller;

import com.mirkamolcode.dto.MonthlyCost;
import com.mirkamolcode.dto.SpendingSummary;
import com.mirkamolcode.service.StatisticsService;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public SpendingSummary summary() {
        return service.mySummary();
    }

    @GetMapping("/monthly-dynamics")
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public List<MonthlyCost> dynamics(@RequestParam(defaultValue = "6") int months) {
        return service.monthlyDynamics(months);
    }
}
