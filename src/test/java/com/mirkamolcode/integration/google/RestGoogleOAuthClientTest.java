package com.mirkamolcode.integration.google;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mirkamolcode.exception.GoogleApiUnavailableException;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestGoogleOAuthClientTest {
    private WireMockServer google;
    private RestGoogleOAuthClient underTest;

    @BeforeEach
    void setUp() {
        google = new WireMockServer(wireMockConfig().dynamicPort());
        google.start();
        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(GoogleApiUnavailableException.class)
                .build());
        underTest = new RestGoogleOAuthClient(RestClient.builder(), retries, google.baseUrl(), "client-id", "client-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        google.stop();
    }

    @Test
    void refreshAccessToken_shouldReturnTokenAndLifetime_whenGoogleAcceptsRefreshToken() {
        // given
        google.stubFor(post("/token").willReturn(okJson(recorded("token-refreshed.json"))));

        // when
        GoogleTokens tokens = underTest.refreshAccessToken("1//refresh");

        // then
        assertThat(tokens.accessToken()).isEqualTo("ya29.a0AfB_byC-recorded-access-token");
        assertThat(tokens.expiresIn()).isEqualTo(Duration.ofSeconds(3599));
        google.verify(postRequestedFor(urlEqualTo("/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=refresh_token"))
                .withRequestBody(containing("refresh_token=1%2F%2Frefresh"))
                .withRequestBody(containing("client_id=client-id"))
                .withRequestBody(containing("client_secret=client-secret")));
    }

    @Test
    void refreshAccessToken_shouldThrowInvalidGrant_whenGoogleSaysTokenIsRevoked() {
        // given
        google.stubFor(post("/token").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("token-invalid-grant.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.refreshAccessToken("1//revoked"))
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("expired or revoked");
    }

    @Test
    void refreshAccessToken_shouldThrowUnavailable_notInvalidGrant_whenClientCredentialsAreWrong() {
        // given
        google.stubFor(post("/token").willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("token-invalid-client.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.refreshAccessToken("1//refresh"))
                .isInstanceOf(GoogleApiUnavailableException.class);
    }

    @Test
    void refreshAccessToken_shouldThrowUnavailable_whenGoogleKeepsFailingWithServerError() {
        // given
        google.stubFor(post("/token").willReturn(aResponse().withStatus(503)));

        // when / then
        assertThatThrownBy(() -> underTest.refreshAccessToken("1//refresh"))
                .isInstanceOf(GoogleApiUnavailableException.class);
    }

    @Test
    void refreshAccessToken_shouldRetryWithBackoff_whenGoogleFailsTemporarily() {
        // given
        google.stubFor(post("/token").inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        google.stubFor(post("/token").inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson(recorded("token-refreshed.json"))));

        // when
        GoogleTokens tokens = underTest.refreshAccessToken("1//refresh");

        // then
        assertThat(tokens.accessToken()).isEqualTo("ya29.a0AfB_byC-recorded-access-token");
        google.verify(2, postRequestedFor(urlEqualTo("/token")));
    }

    @Test
    void refreshAccessToken_shouldNotRetry_whenGoogleSaysTokenIsRevoked() {
        // given
        google.stubFor(post("/token").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody(recorded("token-invalid-grant.json"))));

        // when / then
        assertThatThrownBy(() -> underTest.refreshAccessToken("1//revoked")).isInstanceOf(InvalidGrantException.class);
        google.verify(1, postRequestedFor(urlEqualTo("/token")));
    }

    @Test
    void refreshAccessToken_shouldGiveUp_afterThreeFailedAttempts() {
        // given
        google.stubFor(post("/token").willReturn(aResponse().withStatus(500)));

        // when / then
        assertThatThrownBy(() -> underTest.refreshAccessToken("1//refresh")).isInstanceOf(GoogleApiUnavailableException.class);
        google.verify(3, postRequestedFor(urlEqualTo("/token")));
    }

    private static String recorded(String fixture) {
        try (InputStream in = RestGoogleOAuthClientTest.class.getResourceAsStream("/google/" + fixture)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
