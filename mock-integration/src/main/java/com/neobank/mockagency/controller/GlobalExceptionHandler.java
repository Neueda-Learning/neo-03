package com.neobank.mockagency.controller;

import com.neobank.mockagency.service.AgencyUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The same stable JSON error shape the module's own {@code GlobalExceptionHandler} returns, so a
 * caller does not have to parse two different error formats depending on who refused it.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * The agency is refusing — kill switch, injected failure rate, or the corpus's always-fails
     * document.
     *
     * <p><b>503, not 500.</b> The caller classifies a 5xx as a retryable outage and 503 is the one
     * that says "try again later" rather than "I am broken". This is the status the whole retry
     * ladder is built to meet.</p>
     */
    @ExceptionHandler(AgencyUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(AgencyUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** An unknown agency slug in the path. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(IllegalArgumentException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return error(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        int newline = message == null ? -1 : message.indexOf('\n');
        return error(HttpStatus.BAD_REQUEST,
                "malformed request body: " + (newline > 0 ? message.substring(0, newline) : message));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
