package com.mirkamolcode.integration.google;

import java.time.Duration;

public record GoogleTokens(String accessToken, Duration expiresIn) {
}
