package com.mirkamolcode.exception;

import com.mirkamolcode.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noRoute(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Not Found", "No endpoint " + e.getHttpMethod() + " /" + e.getResourcePath()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "Not Found", e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS", "Unauthorized", e.getMessage()));
    }

    @ExceptionHandler(AttachmentTooLargeException.class)
    public ResponseEntity<ErrorResponse> attachmentTooLarge(AttachmentTooLargeException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), "ATTACHMENT_TOO_LARGE", "Unprocessable Entity", e.getMessage()));
    }

    @ExceptionHandler(SyncAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> syncAlreadyRunning(SyncAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "SYNC_ALREADY_RUNNING", "Conflict", e.getMessage()));
    }

    @ExceptionHandler(GmailDisconnectedException.class)
    public ResponseEntity<ErrorResponse> gmailDisconnected(GmailDisconnectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "GMAIL_RECONNECT_REQUIRED", "Conflict", e.getMessage()));
    }

    @ExceptionHandler(GoogleApiUnavailableException.class)
    public ResponseEntity<ErrorResponse> googleUnavailable(GoogleApiUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(), "GOOGLE_UNAVAILABLE", "Service Unavailable", "Google is temporarily unavailable"));
    }

    @ExceptionHandler(ExchangeRatesUnavailableException.class)
    public ResponseEntity<ErrorResponse> exchangeRatesUnavailable(ExchangeRatesUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE.value(), "EXCHANGE_RATES_UNAVAILABLE", "Service Unavailable", "Exchange rates temporarily unavailable"));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> forbidden(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Forbidden", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT.value(), "CONFLICT", "Conflict", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<ErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();

        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
            fieldErrors.add(new ErrorResponse.FieldErrorItem(error.getField(), error.getDefaultMessage()));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation("Invalid input fields", fieldErrors, fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<ErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();

        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String field = propertyPath.contains(".") ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1) : propertyPath;
            fields.put(field, violation.getMessage());
            fieldErrors.add(new ErrorResponse.FieldErrorItem(field, violation.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation("Invalid input fields", fieldErrors, fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handlerMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<ErrorResponse.FieldErrorItem> fieldErrors = new ArrayList<>();

        for (var result : e.getParameterValidationResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            for (var error : result.getResolvableErrors()) {
                String msg = error.getDefaultMessage();
                fields.put(paramName != null ? paramName : "parameter", msg);
                fieldErrors.add(new ErrorResponse.FieldErrorItem(paramName != null ? paramName : "parameter", msg));
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation("Invalid input fields", fieldErrors, fields));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Bad Request", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "Internal Server Error", "An unexpected error occurred"));
    }
}
