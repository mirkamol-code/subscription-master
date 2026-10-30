package com.mirkamolcode.integration.gmail;

public class GmailUnauthorizedException extends RuntimeException {
    public GmailUnauthorizedException(String message) {
        super(message);
    }
}
