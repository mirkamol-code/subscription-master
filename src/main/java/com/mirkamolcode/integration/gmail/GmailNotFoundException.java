package com.mirkamolcode.integration.gmail;

public class GmailNotFoundException extends RuntimeException {
    public GmailNotFoundException(String message) {
        super(message);
    }
}
