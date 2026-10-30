package com.mirkamolcode.service;

import com.mirkamolcode.dto.AuthDtos.RefreshRequest;
import com.mirkamolcode.entity.RefreshToken;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.UnauthorizedException;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.security.jwt.JwtService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private LoginCodeService loginCodeService;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUserService currentUserService;
    @InjectMocks
    private AuthService underTest;

    @Test
    void refresh_shouldRotateToken_whenRefreshTokenIsValid() {
        User user = user("person@example.com");
        RefreshToken stored = new RefreshToken(user, "stored-hash", Instant.now().plusSeconds(3600));
        when(jwtService.isRefreshToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(stored));
        stubIssuedTokens();

        var result = underTest.refresh(new RefreshRequest("old-refresh-token"));

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_shouldRejectInvalidRevokedAndExpiredTokens() {
        when(jwtService.isRefreshToken("not-refresh")).thenReturn(false);

        assertThatThrownBy(() -> underTest.refresh(new RefreshRequest("not-refresh")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        User user = user("person@example.com");
        RefreshToken revoked = new RefreshToken(user, "hash", Instant.now().plusSeconds(3600));
        revoked.revoke();
        when(jwtService.isRefreshToken("revoked-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> underTest.refresh(new RefreshRequest("revoked-token")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expired or revoked");

        RefreshToken expired = new RefreshToken(user, "hash", Instant.now().minusSeconds(1));
        when(jwtService.isRefreshToken("expired-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> underTest.refresh(new RefreshRequest("expired-token")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expired or revoked");
    }

    @Test
    void logout_shouldRevokeExistingRefreshToken() {
        User user = user("person@example.com");
        RefreshToken stored = new RefreshToken(user, "hash", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any(String.class))).thenReturn(Optional.of(stored));

        underTest.logout(new com.mirkamolcode.dto.AuthDtos.LogoutRequest("valid-refresh-token"));

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void me_shouldReturnCurrentUserProfile() {
        User user = user("person@example.com");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 104L);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-01-15T10:00:00Z"));
        when(currentUserService.requiredUser()).thenReturn(user);

        var result = underTest.me();

        assertThat(result.id()).isEqualTo(104L);
        assertThat(result.email()).isEqualTo("person@example.com");
        assertThat(result.roles()).containsExactly("ROLE_USER");
        assertThat(result.baseCurrency()).isEqualTo("UZS");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
    }

    private void stubIssuedTokens() {
        when(jwtService.accessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.refreshToken(any(User.class))).thenReturn("refresh-token");
        when(jwtService.refreshExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtService.accessSeconds()).thenReturn(900L);
    }

    private User user(String email) {
        return new User(email, Set.of(Role.USER));
    }
}
