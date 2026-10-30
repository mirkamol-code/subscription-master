package com.mirkamolcode.integration.push;

public interface PushSender {
    String publicKey();

    PushResult send(PushTarget target);
}
