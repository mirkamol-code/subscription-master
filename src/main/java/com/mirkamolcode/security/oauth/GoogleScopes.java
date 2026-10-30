package com.mirkamolcode.security.oauth;

import java.util.Set;

public final class GoogleScopes {
    public static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";
    public static final String GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send";
    public static final Set<String> GMAIL = Set.of(GMAIL_READONLY, GMAIL_SEND);

    private GoogleScopes() {
    }
}
