package com.mirkamolcode.integration.google;

public interface GoogleOAuthClient {
    GoogleTokens refreshAccessToken(String refreshToken);
}
