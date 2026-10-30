package com.mirkamolcode.dto.response;

public record RenderedEmail(String html, String contentSecurityPolicy, int blockedRemoteImages) {
}
