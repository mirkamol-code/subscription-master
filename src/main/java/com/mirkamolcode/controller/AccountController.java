package com.mirkamolcode.controller;

import com.mirkamolcode.dto.response.DisconnectGmailResponse;
import com.mirkamolcode.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @DeleteMapping("/gmail")
    @PreAuthorize("hasAuthority('GMAIL_MANAGE_OWN')")
    public DisconnectGmailResponse disconnectGmail() {
        return service.disconnectGmail();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ACCOUNT_DELETE_OWN')")
    public void deleteAccount() {
        service.deleteAccount();
    }
}
