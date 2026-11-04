package com.mirkamolcode.service;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

import jakarta.persistence.PersistenceException;

final class LoggableError {
    private LoggableError() {
    }

    static String describe(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof DataAccessException || cause instanceof PersistenceException || cause instanceof SQLException)
                return failure.getClass().getSimpleName() + ": database rejected the write (details withheld)";
        return failure.getMessage();
    }
}
