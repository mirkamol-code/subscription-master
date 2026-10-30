package com.mirkamolcode.integration.gmail;

import com.mirkamolcode.exception.GoogleApiUnavailableException;

public class GmailTransientException extends GoogleApiUnavailableException {
    public GmailTransientException(String message) {
        super(message);
    }

    public GmailTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
