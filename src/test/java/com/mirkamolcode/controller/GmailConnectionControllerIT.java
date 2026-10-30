package com.mirkamolcode.controller;

import com.mirkamolcode.AbstractTestConfig;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.security.TokenCipher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class GmailConnectionControllerIT extends AbstractTestConfig {
    @Autowired
    private TokenCipher tokenCipher;

    @Test
    void status_shouldShowConnected_withoutExposingRefreshToken() {
        // given
        String authorization = bearer(signIn("person@gmail.com"));
        User user = userRepository.findByEmailIgnoreCase("person@gmail.com").orElseThrow();
        String encrypted = tokenCipher.encrypt("1//secret-refresh");
        gmailConnectionRepository.save(new GmailConnection(user, "person@gmail.com", encrypted, "gmail.readonly gmail.send"));

        // when
        String body = webTestClient.get()
                .uri(API + "/me/gmail")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("CONNECTED")
                .jsonPath("$.gmailAddress").isEqualTo("person@gmail.com")
                .returnResult()
                .toString();

        // then
        assertThat(body).doesNotContain("1//secret-refresh").doesNotContain(encrypted);
    }

    @Test
    void status_shouldAskForReconnect_whenGmailWasRevoked() {
        // given
        String authorization = bearer(signIn("revoked@gmail.com"));
        User user = userRepository.findByEmailIgnoreCase("revoked@gmail.com").orElseThrow();
        GmailConnection connection = new GmailConnection(user, "revoked@gmail.com", tokenCipher.encrypt("1//x"), "gmail.readonly gmail.send");
        connection.disconnect();
        gmailConnectionRepository.save(connection);

        // when / then
        webTestClient.get()
                .uri(API + "/me/gmail")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(false)
                .jsonPath("$.status").isEqualTo("DISCONNECTED");
    }

    @Test
    void status_shouldShowNotConnected_whenUserNeverGrantedGmail() {
        // given
        String authorization = bearer(signIn("never@gmail.com"));

        // when / then
        webTestClient.get()
                .uri(API + "/me/gmail")
                .header("Authorization", authorization)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").isEqualTo(false)
                .jsonPath("$.status").isEqualTo("NOT_CONNECTED");
    }

    @Test
    void status_shouldRequireAuthentication() {
        // when / then
        webTestClient.get().uri(API + "/me/gmail").exchange().expectStatus().isUnauthorized();
    }
}
