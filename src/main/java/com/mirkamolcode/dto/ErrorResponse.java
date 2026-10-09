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
        String error,
        String message,
        Map<String, String> fields,
        List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(String field, String message) {
    }

    public static ErrorResponse validation(String message, List<FieldErrorItem> fieldErrors, Map<String, String> fields) {
        return new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Validation Failed",
                message != null ? message : "Invalid input fields",
                fields,
                fieldErrors
        );
    }

    public static ErrorResponse of(int status, String code, String error, String message) {
        return new ErrorResponse(
                Instant.now(),
                status,
                code,
                error,
                message,
                null,
                null
        );
    }
}
