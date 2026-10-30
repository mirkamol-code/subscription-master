package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.GmailConnectionResponse;
import com.mirkamolcode.service.GmailConnectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/gmail")
public class GmailConnectionController {
    private final GmailConnectionService service;

    public GmailConnectionController(GmailConnectionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GMAIL_MANAGE_OWN')")
    public GmailConnectionResponse getStatus() {
        return service.findStatus();
    }
}
