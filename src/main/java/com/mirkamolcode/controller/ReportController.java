package com.mirkamolcode.controller;

import com.mirkamolcode.model.ReportColumn;
import com.mirkamolcode.service.ReportService;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private static final String REPORT_FILENAME = "subscriptions-annual-report";

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping(value = "/annual.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) List<ReportColumn> columns) {
        return download(service.csv(chooseColumns(columns)), REPORT_FILENAME + ".csv");
    }

    @GetMapping(value = "/annual.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAuthority('STATISTICS_READ_OWN')")
    public ResponseEntity<byte[]> xlsx(@RequestParam(required = false) List<ReportColumn> columns) {
        return download(service.xlsx(chooseColumns(columns)), REPORT_FILENAME + ".xlsx");
    }

    private static List<ReportColumn> chooseColumns(List<ReportColumn> requested) {
        return requested == null ? Arrays.asList(ReportColumn.values()) : requested;
    }

    private static ResponseEntity<byte[]> download(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionHeader.build(true, filename))
                .body(body);
    }
}
