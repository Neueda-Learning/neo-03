package com.neobank.module.controller;

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
 * Turns exceptions into a stable JSON error shape, so the front end and the orchestrator get a
 * predictable body instead of a stack trace — {@code server.error.include-*=never} in
 * {@code application.yml} makes sure nothing leaks past this class.
 *
 * <p>Add a handler per exception your own code throws. A lookup that finds nothing, for example:</p>
 *
 * <pre>{@code
 * @ExceptionHandler(NoSuchElementException.class)
 * public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
 *     return error(HttpStatus.NOT_FOUND, ex.getMessage());
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A field failed validation — in practice, an envelope with no {@code applicationId}. The
     * message names the field, because "400 Bad Request" alone tells the sender nothing.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return error(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * The body was not readable at all — broken JSON, or a value of the wrong type.
     *
     * <p>Worth handling explicitly because you will meet it: the sidecar lets you edit the envelope
     * before sending, and a stray comma otherwise comes back as an empty {@code 400} with nothing
     * to read. Only the first line of Jackson's message is returned; the rest is a parser trace
     * that means nothing to the caller.</p>
     */
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
