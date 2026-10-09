package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.UserProfileResponse;
import com.mirkamolcode.model.Role;
import com.mirkamolcode.exception.ConflictException;
import com.mirkamolcode.exception.ForbiddenException;
import com.mirkamolcode.entity.RefreshToken;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.repository.UserRepository;
import com.mirkamolcode.security.jwt.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirkamolcode.dto.AuthDtos.*;

@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final CurrentUserService currentUser;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwords,
                       JwtService jwt,
                       CurrentUserService currentUser) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.currentUser = currentUser;
    }

    public TokenResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email()))
            throw new ConflictException("Email is already registered");

        User user = users.save(
                new User(request.email().trim().toLowerCase(),
                        passwords.encode(request.password()),
                        Set.of(Role.USER)));

        return issue(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(
                        () -> new ForbiddenException("Invalid credentials"));
        if (!passwords.matches(request.password(), user.getPasswordHash()))
            throw new ForbiddenException("Invalid credentials");
        return issue(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        if (!jwt.isRefreshToken(request.refreshToken()))
            throw new ForbiddenException("Invalid refresh token");

        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new ForbiddenException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now()))
            throw new ForbiddenException("Refresh token expired or revoked");
        stored.revoke();
        return issue(stored.getUser());
    }

    public void logout(LogoutRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return;
        }
        String tokenHash = hash(request.refreshToken());
        refreshTokens.findByTokenHash(tokenHash).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me() {
        User user = currentUser.requiredUser();
        List<String> roles = user.getRoles().stream()
                .map(r -> "ROLE_" + r.name())
                .sorted()
                .toList();
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                roles,
                "UZS",
                user.getCreatedAt()
        );
    }

    private TokenResponse issue(User user) {
        String access = jwt.accessToken(user);
        String refresh = jwt.refreshToken(user);
        refreshTokens.save(new RefreshToken(user, hash(refresh), jwt.refreshExpiry()));
        return new TokenResponse(access, refresh, "Bearer", jwt.accessSeconds());
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
