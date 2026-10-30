package com.mirkamolcode.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirkamolcode.security.TokenCipher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final String KEY_PURPOSE = "oauth2-authorization-request";
    private static final Duration MAX_AGE = Duration.ofMinutes(3);

    private final TokenCipher cipher;
    private final ObjectMapper mapper;

    public HttpCookieOAuth2AuthorizationRequestRepository(TokenCipher cipher) {
        this.cipher = cipher.derive(KEY_PURPOSE);
        this.mapper = new ObjectMapper().registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        if (request.getCookies() == null)
            return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            writeCookie(request, response, "", Duration.ZERO);
            return;
        }
        writeCookie(request, response, serialize(authorizationRequest), MAX_AGE);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        writeCookie(request, response, "", Duration.ZERO);
        return authorizationRequest;
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            return cipher.encrypt(mapper.writeValueAsString(authorizationRequest));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store OAuth2 authorization request", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            return mapper.readValue(cipher.decrypt(value), OAuth2AuthorizationRequest.class);
        } catch (JsonProcessingException | IllegalStateException e) {
            return null;
        }
    }

    private static void writeCookie(HttpServletRequest request, HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(request.getContextPath().isEmpty() ? "/" : request.getContextPath())
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
