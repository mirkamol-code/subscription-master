package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.AuthDtos.LoginRequest;
import com.mirkamolcode.dto.AuthDtos.RefreshRequest;
import com.mirkamolcode.dto.AuthDtos.RegisterRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractTestConfig {

    @Test
    void register_shouldReturnCreatedAndTokens_whenRequestIsValid() {
        webTestClient.post()
                .uri(API + "/auth/register")
                .bodyValue(new RegisterRequest("person@example.com", "a-long-enough-password"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TokenResponse.class)
                .value(tokens -> {
                    assertThat(tokens.accessToken()).isNotBlank();
                    assertThat(tokens.refreshToken()).isNotBlank();
                    assertThat(tokens.tokenType()).isEqualTo("Bearer");
                });
    }

    @Test
    void register_shouldReturnBadRequest_whenPayloadIsInvalid() {
        webTestClient.post()
                .uri(API + "/auth/register")
                .bodyValue(new RegisterRequest("not-an-email", "short"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.fields.email").exists()
                .jsonPath("$.fields.password").exists();
    }

    @Test
    void register_shouldReturnConflict_whenEmailAlreadyExists() {
        register("person@example.com");

        webTestClient.post()
                .uri(API + "/auth/register")
                .bodyValue(new RegisterRequest("person@example.com", "a-long-enough-password"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONFLICT");
    }

    @Test
    void login_shouldReturnTokensForValidCredentials_andUnauthorizedForInvalidCredentials() {
        register("person@example.com");

        webTestClient.post()
                .uri(API + "/auth/login")
                .bodyValue(new LoginRequest("person@example.com", "a-long-enough-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(tokens -> assertThat(tokens.accessToken()).isNotBlank());

        webTestClient.post()
                .uri(API + "/auth/login")
                .bodyValue(new LoginRequest("person@example.com", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_CREDENTIALS")
                .jsonPath("$.message").isEqualTo("Invalid credentials");
    }

    @Test
    void refresh_shouldRotateValidRefreshToken_andRejectItsReuse() {
        TokenResponse tokens = register("person@example.com");

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
        TokenResponse tokens = register("me-user@example.com");

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
    void login_shouldExposeRetryAfterHeader_toTheFrontendOrigin() {
        webTestClient.post()
                .uri(API + "/auth/login")
                .header("Origin", "http://localhost:5173")
                .bodyValue(new LoginRequest("nobody@example.com", "wrong-password"))
                .exchange()
                .expectHeader().value("Access-Control-Expose-Headers",
                        exposed -> assertThat(exposed).contains("Retry-After"));
    }

    @Test
    void me_shouldReturnUnauthorized_whenTokenIsMissing() {
        webTestClient.get()
                .uri(API + "/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logout_shouldRevokeRefreshToken_andPreventSubsequentRefresh() {
        TokenResponse tokens = register("logout-user@example.com");

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
