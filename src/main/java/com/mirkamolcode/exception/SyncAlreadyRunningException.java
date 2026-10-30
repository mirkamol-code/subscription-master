package com.mirkamolcode.exception;
public class SyncAlreadyRunningException extends RuntimeException {
    public SyncAlreadyRunningException() {
        super("A Gmail sync is already running");
    }
}
