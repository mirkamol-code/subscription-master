package com.mirkamolcode.integration.google;

import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.model.GmailConnectionStatus;
import com.mirkamolcode.repository.GmailConnectionRepository;
import com.mirkamolcode.security.TokenCipher;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenServiceTest {
    private static final long USER_ID = 7L;

    private final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
    private final FakeGoogleOAuthClient google = new FakeGoogleOAuthClient();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T09:00:00Z"));
    private GmailConnection connection;
    private GoogleTokenService underTest;

    @BeforeEach
    void setUp() {
        connection = new GmailConnection(mock(User.class), "person@gmail.com", cipher.encrypt("stored-refresh-token"), "gmail.readonly gmail.send");
        when(connections.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        underTest = new GoogleTokenService(connections, google, cipher, clock);
    }

    @Test
    void accessToken_shouldRefreshWithDecryptedRefreshToken_whenNoTokenIsCached() {
        // given
        google.nextTokens = new GoogleTokens("access-1", Duration.ofHours(1));

        // when
        String accessToken = underTest.accessToken(USER_ID);

        // then
        assertThat(accessToken).isEqualTo("access-1");
        assertThat(google.refreshTokensUsed).containsExactly("stored-refresh-token");
    }

    @Test
    void accessToken_shouldReuseCachedToken_whenItHasNotExpired() {
        // given
        google.nextTokens = new GoogleTokens("access-1", Duration.ofHours(1));
        underTest.accessToken(USER_ID);
        clock.advance(Duration.ofMinutes(50));

        // when
        String accessToken = underTest.accessToken(USER_ID);

        // then
        assertThat(accessToken).isEqualTo("access-1");
        assertThat(google.refreshTokensUsed).hasSize(1);
    }

    @Test
    void accessToken_shouldRefreshAgain_whenCachedTokenIsAboutToExpire() {
        // given
        google.nextTokens = new GoogleTokens("access-1", Duration.ofHours(1));
        underTest.accessToken(USER_ID);
        clock.advance(Duration.ofMinutes(59).plusSeconds(30));
        google.nextTokens = new GoogleTokens("access-2", Duration.ofHours(1));

        // when
        String accessToken = underTest.accessToken(USER_ID);

        // then
        assertThat(accessToken).isEqualTo("access-2");
        assertThat(google.refreshTokensUsed).hasSize(2);
    }

    @Test
    void accessToken_shouldMarkConnectionDisconnected_whenGoogleRejectsRefreshToken() {
        // given
        google.rejectRefreshToken = true;

        // when / then
        assertThatThrownBy(() -> underTest.accessToken(USER_ID)).isInstanceOf(GmailDisconnectedException.class);
        assertThat(connection.getStatus()).isEqualTo(GmailConnectionStatus.DISCONNECTED);
    }

    @Test
    void accessToken_shouldUseNewRefreshToken_whenUserReconnectedAfterTokenWasCached() {
        // given
        google.nextTokens = new GoogleTokens("access-1", Duration.ofHours(1));
        underTest.accessToken(USER_ID);
        connection.reconnect("person@gmail.com", cipher.encrypt("new-refresh-token"), "gmail.readonly gmail.send");
        google.nextTokens = new GoogleTokens("access-2", Duration.ofHours(1));

        // when
        String accessToken = underTest.accessToken(USER_ID);

        // then
        assertThat(accessToken).isEqualTo("access-2");
        assertThat(google.refreshTokensUsed).containsExactly("stored-refresh-token", "new-refresh-token");
    }

    @Test
    void accessToken_shouldThrowDisconnected_whenConnectionWasDisconnectedAfterTokenWasCached() {
        // given
        google.nextTokens = new GoogleTokens("access-1", Duration.ofHours(1));
        underTest.accessToken(USER_ID);
        connection.disconnect();

        // when / then
        assertThatThrownBy(() -> underTest.accessToken(USER_ID)).isInstanceOf(GmailDisconnectedException.class);
    }

    @Test
    void accessToken_shouldNotCallGoogle_whenConnectionIsAlreadyDisconnected() {
        // given
        connection.disconnect();

        // when / then
        assertThatThrownBy(() -> underTest.accessToken(USER_ID)).isInstanceOf(GmailDisconnectedException.class);
        assertThat(google.refreshTokensUsed).isEmpty();
    }

    @Test
    void accessToken_shouldThrowDisconnected_whenUserNeverConnectedGmail() {
        // given
        when(connections.findByUserId(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> underTest.accessToken(99L)).isInstanceOf(GmailDisconnectedException.class);
    }

    private static final class FakeGoogleOAuthClient implements GoogleOAuthClient {
        private final List<String> revokedTokens = new CopyOnWriteArrayList<>();

        private final List<String> refreshTokensUsed = new ArrayList<>();
        private GoogleTokens nextTokens;
        private boolean rejectRefreshToken;

        @Override
        public boolean revoke(String token) {
            revokedTokens.add(token);
            return true;
        }

        @Override
        public GoogleTokens refreshAccessToken(String refreshToken) {
            refreshTokensUsed.add(refreshToken);
            if (rejectRefreshToken)
                throw new InvalidGrantException("Token has been expired or revoked.");
            return nextTokens;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
