package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.AuthDtos.ExchangeRequest;
import com.mirkamolcode.dto.AuthDtos.RefreshRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractTestConfig {

    @Test
    void refresh_shouldRotateValidRefreshToken_andRejectItsReuse() {
        TokenResponse tokens = signIn("person@example.com");

        TokenResponse rotated = webTestClient.post()
                .uri(API + "/auth/refresh")
                .bodyValue(new RefreshRequest(tokens.refreshToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(rotated).isNotNull();
        assertThat(rotated.accessToken()).isNotBlank();

        webTestClient.post()
                .uri(API + "/auth/refresh")
                .bodyValue(new RefreshRequest(tokens.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void refresh_shouldReturnUnauthorized_whenTokenIsMalformed() {
        webTestClient.post()
                .uri(API + "/auth/refresh")
                .bodyValue(new RefreshRequest("not-a-jwt"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void me_shouldReturnCurrentUserProfile_whenAuthenticated() {
        TokenResponse tokens = signIn("me-user@example.com");

        webTestClient.get()
                .uri(API + "/auth/me")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("me-user@example.com")
                .jsonPath("$.roles[0]").isEqualTo("ROLE_USER")
                .jsonPath("$.baseCurrency").isEqualTo("UZS")
                .jsonPath("$.createdAt").exists();
    }

    @Test
    void exchange_shouldExposeRetryAfterHeader_toTheFrontendOrigin() {
        webTestClient.post()
                .uri(API + "/auth/exchange")
                .header("Origin", "http://localhost:5173")
                .bodyValue(new ExchangeRequest("unknown-code"))
                .exchange()
                .expectHeader().value("Access-Control-Expose-Headers",
                        exposed -> assertThat(exposed).contains("Retry-After"));
    }

    @Test
    void me_shouldReturnUnauthorized_whenTokenIsMissing() {
        webTestClient.get()
                .uri(API + "/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.errorCode").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("Authentication required");
    }

    @Test
    void logout_shouldRevokeRefreshToken_andPreventSubsequentRefresh() {
        TokenResponse tokens = signIn("logout-user@example.com");

        webTestClient.post()
                .uri(API + "/auth/logout")
                .bodyValue(new com.mirkamolcode.dto.AuthDtos.LogoutRequest(tokens.refreshToken()))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.post()
                .uri(API + "/auth/refresh")
                .bodyValue(new RefreshRequest(tokens.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_CREDENTIALS");
    }
}
