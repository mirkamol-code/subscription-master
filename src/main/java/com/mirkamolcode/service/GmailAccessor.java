package com.mirkamolcode.service;

import com.mirkamolcode.exception.GmailDisconnectedException;
import com.mirkamolcode.integration.gmail.GmailUnauthorizedException;
import com.mirkamolcode.integration.google.GoogleTokenService;

import java.util.function.Function;

import org.springframework.stereotype.Component;

@Component
public class GmailAccessor {
    private final GoogleTokenService tokens;

    public GmailAccessor(GoogleTokenService tokens) {
        this.tokens = tokens;
    }

    public void markDisconnected(Long userId) {
        tokens.markDisconnected(userId);
    }

    public <T> T callWithFreshToken(Long userId, Function<String, T> gmailCall) {
        try {
            return gmailCall.apply(tokens.accessToken(userId));
        } catch (GmailUnauthorizedException rejected) {
            tokens.invalidate(userId);
            try {
                return gmailCall.apply(tokens.accessToken(userId));
            } catch (GmailUnauthorizedException stillRejected) {
                tokens.markDisconnected(userId);
                throw new GmailDisconnectedException("Gmail rejected a freshly refreshed access token");
            }
        }
    }
}
