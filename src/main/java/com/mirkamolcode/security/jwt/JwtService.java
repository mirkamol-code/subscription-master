package com.mirkamolcode.security.jwt;

import com.mirkamolcode.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.access-token-minutes}") long minutes, @Value("${app.jwt.refresh-token-days}") long days) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        accessTtl = Duration.ofMinutes(minutes);
        refreshTtl = Duration.ofDays(days);
    }

    public String accessToken(User user) {
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.permissions().stream())
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
        return Jwts.builder().subject(user.getEmail()).claim("roles", user.getRoles().stream().map(Enum::name).toList()).claim("permissions", permissions).issuedAt(new Date()).expiration(Date.from(Instant.now().plus(accessTtl))).signWith(key).compact();
    }

    public String refreshToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(refreshTtl)))
                .signWith(key).compact();
    }

    public io.jsonwebtoken.Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String subject(String token) {
        return claims(token).getSubject();
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(claims(token).get("type"));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Instant refreshExpiry() {
        return Instant.now().plus(refreshTtl);
    }

    public long accessSeconds() {
        return accessTtl.toSeconds();
    }
}
