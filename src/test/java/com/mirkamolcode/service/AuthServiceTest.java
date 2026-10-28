package com.mirkamolcode.service;

import com.mirkamolcode.dto.AuthDtos.LoginRequest;
import com.mirkamolcode.dto.AuthDtos.RefreshRequest;
import com.mirkamolcode.dto.AuthDtos.RegisterRequest;
import com.mirkamolcode.entity.RefreshToken;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.exception.ConflictException;
import com.mirkamolcode.exception.UnauthorizedException;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.repository.UserRepository;
import com.mirkamolcode.security.jwt.JwtService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUserService currentUserService;
    @InjectMocks
    private AuthService underTest;

    @Test
    void register_shouldNormalizeEmailAndIssueTokens_whenRequestIsValid() {
        RegisterRequest request = new RegisterRequest(" Person@Example.COM ", "a-long-enough-password");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubIssuedTokens();

        var result = underTest.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("person@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getRoles()).containsExactly(Role.USER);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(argThat(token ->
                token.getTokenHash().length() == 64 && !token.getTokenHash().equals("refresh-token")));
    }

    @Test
    void register_shouldThrowConflict_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("person@example.com", "a-long-enough-password");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        assertThatThrownBy(() -> underTest.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, refreshTokenRepository);
    }

    @Test
    void login_shouldIssueTokens_whenCredentialsAreValid() {
        User user = user("person@example.com");
        LoginRequest request = new LoginRequest(user.getEmail(), "a-long-enough-password");
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        stubIssuedTokens();

        var result = underTest.login(request);

        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_shouldRejectInvalidCredentials_withoutRevealingWhetherUserExists() {
        LoginRequest missingUser = new LoginRequest("missing@example.com", "wrong-password");
        when(userRepository.findByEmailIgnoreCase(missingUser.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underTest.login(missingUser))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");

        User user = user("person@example.com");
        LoginRequest wrongPassword = new LoginRequest(user.getEmail(), "wrong-password");
        when(userRepository.findByEmailIgnoreCase(wrongPassword.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(wrongPassword.password(), user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> underTest.login(wrongPassword))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");
        verify(refreshTokenRepository, never()).save(any());
    }

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
        return new User(email, "bcrypt-hash", Set.of(Role.USER));
    }
}
