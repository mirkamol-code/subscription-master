package com.mirkamolcode.security.oauth;

import com.mirkamolcode.service.GoogleLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);

    private final GoogleLoginService loginService;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final FrontendCallback frontendCallback;

    public GoogleLoginSuccessHandler(GoogleLoginService loginService,
                                     OAuth2AuthorizedClientService authorizedClients,
                                     FrontendCallback frontendCallback) {
        this.loginService = loginService;
        this.authorizedClients = authorizedClients;
        this.frontendCallback = frontendCallback;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = takeAuthorizedClient(token);
        try {
            String code = loginService.completeLogin(mapToGoogleProfile((OidcUser) token.getPrincipal()), extractRefreshToken(client), extractScopes(client));
            response.sendRedirect(frontendCallback.buildSuccessUrl(code));
        } catch (RuntimeException e) {
            log.warn("Google login could not be completed: {}", e.getMessage());
            response.sendRedirect(frontendCallback.buildFailureUrl());
        }
    }

    private OAuth2AuthorizedClient takeAuthorizedClient(OAuth2AuthenticationToken token) {
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(registrationId, token.getName());
        authorizedClients.removeAuthorizedClient(registrationId, token.getName());
        return client;
    }

    private static GoogleProfile mapToGoogleProfile(OidcUser user) {
        return new GoogleProfile(user.getSubject(), user.getEmail(), Boolean.TRUE.equals(user.getEmailVerified()),
                user.getFullName(), user.getPicture());
    }

    private static String extractRefreshToken(OAuth2AuthorizedClient client) {
        return client != null && client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
    }

    private static Set<String> extractScopes(OAuth2AuthorizedClient client) {
        return client != null ? client.getAccessToken().getScopes() : Set.of();
    }
}
