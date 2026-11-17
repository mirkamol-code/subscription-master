package com.mirkamolcode.security.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void constructor_shouldRefuseSecret_whenShorterThan32Bytes() {
        // given
        String secret = "a".repeat(31);

        // when / then
        assertThatThrownBy(() -> new JwtService(secret, 15, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT_SECRET must be at least 32 bytes");
    }

    @Test
    void constructor_shouldAcceptSecret_whenExactly32Bytes() {
        // given
        String secret = "a".repeat(32);

        // when / then
        assertThatCode(() -> new JwtService(secret, 15, 30)).doesNotThrowAnyException();
    }
}
