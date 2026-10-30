package com.mirkamolcode.security.oauth;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.dto.AuthDtos.ExchangeRequest;
import com.mirkamolcode.dto.AuthDtos.TokenResponse;
import com.mirkamolcode.entity.LoginCode;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.security.TokenCipher;
import com.mirkamolcode.security.TokenHashes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleLoginIT extends AbstractTestConfig {
    private static final Set<String> ALL_SCOPES = Set.of("openid", "email", "profile",
            "https://www.googleapis.com/auth/gmail.readonly", "https://www.googleapis.com/auth/gmail.send");

    @Autowired
    private AuthenticationSuccessHandler googleLoginSuccessHandler;
    @Autowired
    private OAuth2AuthorizedClientService authorizedClients;
    @Autowired
    private ClientRegistrationRepository clientRegistrations;
    @Autowired
    private TokenCipher tokenCipher;

    @Test
    void firstLogin_shouldCreateUserFromGoogleProfile() throws Exception {
        // when
        String redirect = completeGoogleLogin("google-sub-1", "New.Person@gmail.com", "1//refresh-1", ALL_SCOPES);

        // then
        assertThat(redirect).startsWith("http://localhost:5173/auth/callback?code=").doesNotContain("1//refresh-1");
        webTestClient.get().uri(API + "/auth/me").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("new.person@gmail.com")
                .jsonPath("$.displayName").isEqualTo("Test Person")
                .jsonPath("$.avatarUrl").isEqualTo("https://lh3.googleusercontent.com/a/photo")
                .jsonPath("$.roles").isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void firstLogin_shouldConnectGmail_andStoreRefreshTokenOnlyEncrypted() throws Exception {
        // when
        String redirect = completeGoogleLogin("google-sub-1b", "connect@gmail.com", "1//refresh-1b", ALL_SCOPES);

        // then
        webTestClient.get().uri(API + "/me/gmail").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(true);
        String stored = storedRefreshTokenOf("google-sub-1b");
        assertThat(stored).doesNotContain("1//refresh-1b");
        assertThat(tokenCipher.decrypt(stored)).isEqualTo("1//refresh-1b");
    }

    @Test
    void login_shouldLinkExistingAccountByEmail_insteadOfCreatingDuplicate() throws Exception {
        // given
        User existing = userRepository.save(new User("existing@gmail.com", Set.of(Role.USER)));

        // when
        String redirect = completeGoogleLogin("google-sub-2", "Existing@gmail.com", "1//refresh-2", ALL_SCOPES);

        // then
        webTestClient.get().uri(API + "/auth/me").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(existing.getId().intValue());
    }

    @Test
    void login_shouldGrantAdminRole_whenEmailIsConfiguredAsAdmin() throws Exception {
        // when
        String redirect = completeGoogleLogin("google-sub-admin", "admin@example.com", "1//refresh-admin", ALL_SCOPES);

        // then
        webTestClient.get().uri(API + "/auth/me").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.roles").isEqualTo(List.of("ROLE_ADMIN", "ROLE_USER"));
    }

    @Test
    void login_shouldRevokeAdminRole_whenEmailIsNoLongerConfiguredAsAdmin() throws Exception {
        // given
        userRepository.save(new User("former-admin@example.com", Set.of(Role.ADMIN)));

        // when
        String redirect = completeGoogleLogin("google-sub-former", "former-admin@example.com", "1//refresh-f", ALL_SCOPES);

        // then
        webTestClient.get().uri(API + "/auth/me").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.roles").isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void login_shouldKeepStoredEmail_whenNewGoogleEmailBelongsToAnotherAccount() throws Exception {
        // given
        completeGoogleLogin("google-sub-mover", "mover@gmail.com", "1//refresh-m", ALL_SCOPES);
        userRepository.save(new User("taken@gmail.com", Set.of(Role.USER)));

        // when
        String redirect = completeGoogleLogin("google-sub-mover", "taken@gmail.com", "1//refresh-m2", ALL_SCOPES);

        // then
        assertThat(redirect).contains("code=");
        webTestClient.get().uri(API + "/auth/me").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("mover@gmail.com");
    }

    @Test
    void login_shouldRefuse_whenGoogleEmailIsNotVerified() throws Exception {
        // when
        String redirect = completeGoogleLogin("google-sub-unverified", "unverified@gmail.com", false, "1//refresh-u", ALL_SCOPES);

        // then
        assertThat(redirect).isEqualTo("http://localhost:5173/auth/callback?error=google_login_failed");
        assertThat(userRepository.findByEmailIgnoreCase("unverified@gmail.com")).isEmpty();
    }

    @Test
    void login_shouldRefuse_whenEmailIsLinkedToAnotherGoogleAccount() throws Exception {
        // given
        completeGoogleLogin("google-sub-owner", "owned@gmail.com", "1//refresh-o", ALL_SCOPES);

        // when
        String redirect = completeGoogleLogin("google-sub-intruder", "owned@gmail.com", "1//refresh-i", ALL_SCOPES);

        // then
        assertThat(redirect).isEqualTo("http://localhost:5173/auth/callback?error=google_login_failed");
    }

    @Test
    void login_shouldReplaceRefreshToken_whenUserSignsInAgain() throws Exception {
        // given
        completeGoogleLogin("google-sub-3", "again@gmail.com", "1//old", ALL_SCOPES);

        // when
        completeGoogleLogin("google-sub-3", "again@gmail.com", "1//new", ALL_SCOPES);

        // then
        assertThat(tokenCipher.decrypt(storedRefreshTokenOf("google-sub-3"))).isEqualTo("1//new");
    }

    @Test
    void login_shouldSignInWithoutConnectingGmail_whenUserDeclinedGmailScopes() throws Exception {
        // when
        String redirect = completeGoogleLogin("google-sub-4", "partial@gmail.com", "1//refresh-4", Set.of("openid", "email", "profile"));

        // then
        webTestClient.get().uri(API + "/me/gmail").header("Authorization", bearerFrom(redirect)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(false)
                .jsonPath("$.status").isEqualTo("NOT_CONNECTED");
    }

    @Test
    void exchange_shouldReturnTokens_whenLoginCodeIsValid() throws Exception {
        // given
        String code = codeFrom(completeGoogleLogin("google-sub-5", "exchange@gmail.com", "1//refresh-5", ALL_SCOPES));

        // when / then
        TokenResponse tokens = webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest(code))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        webTestClient.get()
                .uri(API + "/auth/me")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("exchange@gmail.com")
                .jsonPath("$.displayName").isEqualTo("Test Person");
    }

    @Test
    void exchange_shouldRejectCode_whenItWasAlreadyUsed() throws Exception {
        // given
        String code = codeFrom(completeGoogleLogin("google-sub-6", "reuse@gmail.com", "1//refresh-6", ALL_SCOPES));
        webTestClient.post().uri(API + "/auth/exchange").bodyValue(new ExchangeRequest(code)).exchange().expectStatus().isOk();

        // when / then
        webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest(code))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Invalid or expired login code");
    }

    @Test
    void exchange_shouldRejectCode_whenItExpired() {
        // given
        User user = userRepository.save(new User("expired@gmail.com", Set.of(Role.USER)));
        loginCodeRepository.save(new LoginCode(user, TokenHashes.sha256("expired-code"), Instant.now().minusSeconds(1)));

        // when / then
        webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest("expired-code"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void exchange_shouldRejectCode_whenItIsUnknown() {
        // when / then
        webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest("never-issued"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String completeGoogleLogin(String subject, String email, String refreshToken, Set<String> grantedScopes) throws Exception {
        return completeGoogleLogin(subject, email, true, refreshToken, grantedScopes);
    }

    private String completeGoogleLogin(String subject, String email, boolean emailVerified, String refreshToken, Set<String> grantedScopes) throws Exception {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(3600), Map.of(
                "sub", subject,
                "email", email,
                "email_verified", emailVerified,
                "name", "Test Person",
                "picture", "https://lh3.googleusercontent.com/a/photo"));
        DefaultOidcUser principal = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        authorizedClients.saveAuthorizedClient(new OAuth2AuthorizedClient(
                clientRegistrations.findByRegistrationId("google"),
                authentication.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "ya29.access", now, now.plusSeconds(3599), grantedScopes),
                new OAuth2RefreshToken(refreshToken, now)), authentication);

        MockHttpServletResponse response = new MockHttpServletResponse();
        googleLoginSuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);
        return response.getRedirectedUrl();
    }

    private String bearerFrom(String redirect) {
        TokenResponse tokens = webTestClient.post()
                .uri(API + "/auth/exchange")
                .bodyValue(new ExchangeRequest(codeFrom(redirect)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody();
        return bearer(tokens);
    }

    private String storedRefreshTokenOf(String googleSubject) {
        Long userId = userRepository.findByGoogleSubject(googleSubject).orElseThrow().getId();
        return gmailConnectionRepository.findByUserId(userId).orElseThrow().getEncryptedRefreshToken();
    }

    private static String codeFrom(String redirect) {
        return UriComponentsBuilder.fromUriString(redirect).build().getQueryParams().getFirst("code");
    }
}
