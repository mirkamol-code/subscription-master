package com.mirkamolcode.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);

    private final FrontendCallback frontendCallback;

    public GoogleLoginFailureHandler(FrontendCallback frontendCallback) {
        this.frontendCallback = frontendCallback;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        log.warn("Google login failed: {}", exception.getMessage());
        response.sendRedirect(frontendCallback.buildFailureUrl());
    }
}
