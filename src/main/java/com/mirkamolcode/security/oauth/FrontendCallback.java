package com.mirkamolcode.security.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FrontendCallback {
    static final String LOGIN_FAILED = "google_login_failed";

    private final String frontendBaseUrl;

    public FrontendCallback(@Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String buildSuccessUrl(String loginCode) {
        return buildUrl("code", loginCode);
    }

    public String buildFailureUrl() {
        return buildUrl("error", LOGIN_FAILED);
    }

    private String buildUrl(String name, String value) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/auth/callback")
                .queryParam(name, value)
                .build()
                .toUriString();
    }
}
