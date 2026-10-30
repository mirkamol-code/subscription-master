package com.mirkamolcode.controller;

import com.mirkamolcode.dto.AuthDtos.*;
import com.mirkamolcode.dto.response.UserProfileResponse;
import com.mirkamolcode.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/exchange")
    public TokenResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        return service.exchange(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return service.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        service.logout(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return service.me();
    }
}
