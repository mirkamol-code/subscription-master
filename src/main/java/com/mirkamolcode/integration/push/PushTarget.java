package com.mirkamolcode.integration.push;

public record PushTarget(String endpoint, String p256dh, String auth, String payload) {
}
