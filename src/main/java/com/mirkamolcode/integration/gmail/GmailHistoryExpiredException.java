package com.mirkamolcode.integration.gmail;

public class GmailHistoryExpiredException extends RuntimeException {
    public GmailHistoryExpiredException(String startHistoryId) {
        super("Gmail history " + startHistoryId + " is no longer available");
    }
}
