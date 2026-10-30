package com.mirkamolcode.security;

import com.mirkamolcode.AbstractTestConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIT extends AbstractTestConfig {

    @Test
    void passwordEndpoints_shouldBeGone_evenForAuthenticatedCaller() {
        // given
        String authorization = bearer(signIn("person@example.com"));

        // when / then
        for (String removed : new String[]{"/auth/register", "/auth/login"}) {
            webTestClient.post()
                    .uri(API + removed)
                    .header("Authorization", authorization)
                    .bodyValue(Map.of("email", "person@example.com", "password", "a-long-enough-password"))
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Test
    void protectedEndpoint_shouldReturnUnauthorized_whenJwtIsMissing() {
        // when / then
        webTestClient.get()
                .uri(API + "/subscriptions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpoint_shouldReturnUnauthorized_whenJwtIsInvalid() {
        // when / then
        webTestClient.get()
                .uri(API + "/subscriptions")
                .header("Authorization", "Bearer not.a.valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpoint_shouldReturnOk_whenJwtIsValid() {
        // given
        String authorization = bearer(signIn("person@example.com"));

        // when / then
        webTestClient.get()
                .uri(API + "/subscriptions")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void googleAuthorization_shouldRedirectToGoogleAskingForOfflineAccessAndConsent() {
        // when
        var result = webTestClient.get()
                .uri(API + "/oauth2/authorization/google")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().exists("Set-Cookie")
                .returnResult(Void.class);

        // then
        String location = result.getResponseHeaders().getLocation().toString();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(query.getFirst("access_type")).isEqualTo("offline");
        assertThat(query.getFirst("prompt")).isEqualTo("consent");
        assertThat(query.getFirst("scope")).contains("gmail.readonly").contains("gmail.send");
        assertThat(result.getResponseHeaders().getFirst("Set-Cookie")).contains("HttpOnly");
        assertThat(result.getResponseHeaders().get("Set-Cookie")).noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
    }
}
