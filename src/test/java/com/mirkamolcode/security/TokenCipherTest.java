package com.mirkamolcode.security;

import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final TokenCipher underTest = new TokenCipher(KEY);

    @Test
    void decrypt_shouldReturnOriginalValue_whenGivenItsCiphertext() {
        // given
        String refreshToken = "1//0g-google-refresh-token";

        // when
        String ciphertext = underTest.encrypt(refreshToken);

        // then
        assertThat(ciphertext).doesNotContain(refreshToken);
        assertThat(underTest.decrypt(ciphertext)).isEqualTo(refreshToken);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertexts_whenCalledTwiceWithSameValue() {
        // when
        String first = underTest.encrypt("same-token");
        String second = underTest.encrypt("same-token");

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void decrypt_shouldReject_whenCiphertextWasTampered() {
        // given
        String ciphertext = underTest.encrypt("refresh-token");
        char last = ciphertext.charAt(ciphertext.length() - 2);
        String tampered = ciphertext.substring(0, ciphertext.length() - 2) + (last == 'A' ? 'B' : 'A') + ciphertext.charAt(ciphertext.length() - 1);

        // when / then
        assertThatThrownBy(() -> underTest.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_shouldReject_whenEncryptedWithAnotherKey() {
        // given
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String ciphertext = new TokenCipher(Base64.getEncoder().encodeToString(otherKey)).encrypt("refresh-token");

        // when / then
        assertThatThrownBy(() -> underTest.decrypt(ciphertext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void derive_shouldGiveEachPurposeItsOwnKey() {
        // given
        TokenCipher cookieCipher = underTest.derive("oauth2-authorization-request");

        // when
        String cookieValue = cookieCipher.encrypt("state");

        // then
        assertThat(cookieCipher.decrypt(cookieValue)).isEqualTo("state");
        assertThatThrownBy(() -> underTest.decrypt(cookieValue)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cookieCipher.decrypt(underTest.encrypt("refresh-token"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_shouldFailFast_whenKeyIsNot32Bytes() {
        // given
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        // when / then
        assertThatThrownBy(() -> new TokenCipher(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void constructor_shouldFailFast_whenKeyIsMissing() {
        // when / then
        assertThatThrownBy(() -> new TokenCipher(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
