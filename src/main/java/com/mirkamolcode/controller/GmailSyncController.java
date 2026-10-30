package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.GmailSyncStatusResponse;
import com.mirkamolcode.service.GmailSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gmail")
public class GmailSyncController {
    private final GmailSyncService service;

    public GmailSyncController(GmailSyncService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('GMAIL_MANAGE_OWN')")
    public GmailSyncStatusResponse requestSync() {
        return service.requestSync();
    }

    @GetMapping("/sync-status")
    @PreAuthorize("hasAuthority('GMAIL_MANAGE_OWN')")
    public GmailSyncStatusResponse getSyncStatus() {
        return service.findStatus();
    }
}
