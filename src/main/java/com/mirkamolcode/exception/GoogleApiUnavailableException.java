package com.mirkamolcode.exception;

public class GoogleApiUnavailableException extends RuntimeException {
    public GoogleApiUnavailableException(String message) {
        super(message);
    }

    public GoogleApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
