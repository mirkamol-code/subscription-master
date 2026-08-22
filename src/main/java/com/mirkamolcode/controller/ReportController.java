package com.mirkamolcode.controller;

import com.mirkamolcode.service.ReportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(
            value = "/annual.csv",
            produces = "text/csv"
    )
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public ResponseEntity<byte[]> csv() {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=annual-subscription-costs.csv")
                .body(service.csv());
    }

    @GetMapping(value = "/annual.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public ResponseEntity<byte[]> xlsx() {
        return ResponseEntity
                .ok()
                .header(HttpHeaders
                        .CONTENT_DISPOSITION,
                        "attachment; filename=annual-subscription-costs.xlsx")
                .body(service.xlsx());
    }
}
