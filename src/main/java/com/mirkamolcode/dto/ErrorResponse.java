package com.mirkamolcode.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String errorCode,
        String error,
        String message,
        Map<String, String> fields,
        List<FieldErrorItem> fieldErrors,
        Long retryAfterSeconds,
        Instant blockedUntil,
        Boolean blocked
) {
    public record FieldErrorItem(String field, String message) {
    }

    public ErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String error,
            String message,
            Map<String, String> fields,
            List<FieldErrorItem> fieldErrors,
            Long retryAfterSeconds,
            Instant blockedUntil,
            Boolean blocked
    ) {
        this(timestamp, status, code, code, error, message, fields, fieldErrors, retryAfterSeconds, blockedUntil, blocked);
    }

    public static ErrorResponse validation(String message, List<FieldErrorItem> fieldErrors, Map<String, String> fields) {
        return new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "VALIDATION_ERROR",
                "Validation Failed",
                message != null ? message : "Invalid input fields",
                fields,
                fieldErrors,
                null,
                null,
                null
        );
    }

    public static ErrorResponse of(int status, String code, String error, String message) {
        return new ErrorResponse(
                Instant.now(),
                status,
                code,
                code,
                error,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    /** TASK-10: returns errorCode = "AUTH_LOCKED" and retryAfterSeconds */
    public static ErrorResponse rateLimited(long retryAfterSeconds, Instant blockedUntil, String message) {
        return new ErrorResponse(
                Instant.now(),
                429,
                "TOO_MANY_REQUESTS",
                "AUTH_LOCKED",
                "Too Many Requests",
                message,
                null,
                null,
                retryAfterSeconds,
                blockedUntil,
                true
        );
    }
}
