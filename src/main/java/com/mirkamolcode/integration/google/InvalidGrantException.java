package com.mirkamolcode.integration.google;

public class InvalidGrantException extends RuntimeException {
    public InvalidGrantException(String message) {
        super(message);
    }
}
