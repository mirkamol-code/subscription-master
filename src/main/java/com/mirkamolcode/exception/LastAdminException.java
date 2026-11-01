package com.mirkamolcode.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("The last remaining admin cannot be demoted");
    }
}
