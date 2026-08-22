package com.mirkamolcode.exception;
import com.mirkamolcode.dto.ErrorResponse;

import java.time.Instant;import java.util.*;import org.springframework.http.*;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.validation.FieldError;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(NotFoundException.class) ResponseEntity<ErrorResponse> notFound(NotFoundException e){return error(HttpStatus.NOT_FOUND,"NOT_FOUND",e.getMessage(),Map.of());}
 @ExceptionHandler({ForbiddenException.class,org.springframework.security.access.AccessDeniedException.class}) ResponseEntity<ErrorResponse> forbidden(RuntimeException e){return error(HttpStatus.FORBIDDEN,"FORBIDDEN",e.getMessage(),Map.of());}
 @ExceptionHandler(ConflictException.class) ResponseEntity<ErrorResponse> conflict(ConflictException e){return error(HttpStatus.CONFLICT,"CONFLICT",e.getMessage(),Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e){Map<String,String> fields=new LinkedHashMap<>();for(FieldError error:e.getBindingResult().getFieldErrors())fields.put(error.getField(),error.getDefaultMessage());return error(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Request validation failed",fields);}
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) ResponseEntity<ErrorResponse> badRequest(Exception e){return error(HttpStatus.BAD_REQUEST,"BAD_REQUEST",e.getMessage(),Map.of());}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> unexpected(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","An unexpected error occurred",Map.of());}
 private ResponseEntity<ErrorResponse> error(HttpStatus status,String code,String message,Map<String,String> fields){return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(),status.value(),code,message,fields));}
}
