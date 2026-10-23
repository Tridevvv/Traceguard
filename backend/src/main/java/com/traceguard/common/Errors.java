package com.traceguard.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Exceptions thrown by services and the single handler that maps them to the API error format. */
public final class Errors {

    private Errors() {}

    public record ApiError(int status, String error, String message) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    @RestControllerAdvice
    static class Handler {

        @ExceptionHandler(NotFoundException.class)
        ResponseEntity<ApiError> notFound(NotFoundException e) { return build(HttpStatus.NOT_FOUND, e.getMessage()); }

        @ExceptionHandler(BadRequestException.class)
        ResponseEntity<ApiError> badRequest(BadRequestException e) { return build(HttpStatus.BAD_REQUEST, e.getMessage()); }

        @ExceptionHandler(ConflictException.class)
        ResponseEntity<ApiError> conflict(ConflictException e) { return build(HttpStatus.CONFLICT, e.getMessage()); }

        @ExceptionHandler(DataIntegrityViolationException.class)
        ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
            return build(HttpStatus.CONFLICT, "Conflicting or duplicate data; retry if this was a concurrent scan");
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
            String msg = e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + " " + f.getDefaultMessage())
                    .reduce((a, b) -> a + "; " + b).orElse("Invalid request");
            return build(HttpStatus.BAD_REQUEST, msg);
        }

        private ResponseEntity<ApiError> build(HttpStatus s, String message) {
            return ResponseEntity.status(s).body(new ApiError(s.value(), s.name(), message));
        }
    }
}
