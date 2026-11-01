package com.mirkamolcode.security.oauth;

public record LoginContext(
        String ip,
        String userAgent
) {
}
