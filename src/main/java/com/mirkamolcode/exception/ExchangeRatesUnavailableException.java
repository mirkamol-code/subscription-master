package com.mirkamolcode.exception;
public class ExchangeRatesUnavailableException extends RuntimeException {
    public ExchangeRatesUnavailableException(String message) {
        super(message);
    }
}
