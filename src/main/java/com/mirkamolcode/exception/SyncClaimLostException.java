package com.mirkamolcode.exception;
public class SyncClaimLostException extends RuntimeException {
    public SyncClaimLostException(Long userId) {
        super("Gmail sync for user " + userId + " was taken over by a newer run");
    }
}
