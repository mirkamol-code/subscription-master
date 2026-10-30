package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.UserProfileResponse;
import com.mirkamolcode.exception.UnauthorizedException;
import com.mirkamolcode.entity.RefreshToken;
import com.mirkamolcode.entity.User;
import com.mirkamolcode.repository.RefreshTokenRepository;
import com.mirkamolcode.security.jwt.JwtService;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirkamolcode.dto.AuthDtos.*;
import static com.mirkamolcode.security.TokenHashes.sha256;

@Service
@Transactional
public class AuthService {
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwt;
    private final CurrentUserService currentUser;
    private final LoginCodeService loginCodes;

    public AuthService(RefreshTokenRepository refreshTokens,
                       JwtService jwt,
                       CurrentUserService currentUser,
                       LoginCodeService loginCodes) {
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.currentUser = currentUser;
        this.loginCodes = loginCodes;
    }

    public TokenResponse exchange(ExchangeRequest request) {
        return issue(loginCodes.consume(request.code()));
    }

    public TokenResponse refresh(RefreshRequest request) {
        if (!jwt.isRefreshToken(request.refreshToken()))
            throw new UnauthorizedException("Invalid refresh token");

        RefreshToken stored = refreshTokens.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now()))
            throw new UnauthorizedException("Refresh token expired or revoked");
        stored.revoke();
        return issue(stored.getUser());
    }

    public void logout(LogoutRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(request.refreshToken())).ifPresent(RefreshToken::revoke);
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
                user.getDisplayName(),
                user.getAvatarUrl(),
                roles,
                "UZS",
                user.getCreatedAt()
        );
    }

    private TokenResponse issue(User user) {
        String access = jwt.accessToken(user);
        String refresh = jwt.refreshToken(user);
        refreshTokens.save(new RefreshToken(user, sha256(refresh), jwt.refreshExpiry()));
        return new TokenResponse(access, refresh, "Bearer", jwt.accessSeconds());
    }
}
