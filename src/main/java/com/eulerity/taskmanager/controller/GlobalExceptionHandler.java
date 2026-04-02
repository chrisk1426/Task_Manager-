package com.eulerity.taskmanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for the entire application.
 *
 * @RestControllerAdvice combines @ControllerAdvice and @ResponseBody.
 * It intercepts exceptions thrown from any @RestController and converts
 * them into consistent JSON error responses before they reach the client.
 *
 * Without this class, Spring would return different error formats depending
 * on the exception type — HTML error pages, default Spring JSON, or raw
 * stack traces. This handler ensures every error the API returns has the
 * same predictable shape:
 *
 *   { "status": 404, "message": "Task not found with id: 5" }
 *
 * The three handlers are ordered from most specific to least specific:
 *   1. ResponseStatusException     — our own application errors (404, 502, 503)
 *   2. MethodArgumentNotValidException — validation failures (@Valid)
 *   3. Exception                   — catch-all fallback for unexpected errors
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Consistent JSON shape for all error responses.
     *
     * A nested record keeps this type scoped to this class — it is only
     * used as a response body shape and does not need to be visible
     * elsewhere in the codebase.
     *
     * @param status  the HTTP status code as an integer (e.g., 404)
     * @param message a human-readable description of the error
     */
    record ErrorResponse(int status, String message) {}

    // -------------------------------------------------------------------------
    // Handler 1: ResponseStatusException
    // -------------------------------------------------------------------------

    /**
     * Handles ResponseStatusException thrown explicitly by the service layer.
     *
     * These are intentional application errors with a specific HTTP status:
     *   - 404 Not Found:           task does not exist (TaskService)
     *   - 503 Service Unavailable: ANTHROPIC_API_KEY not configured (AiService)
     *   - 502 Bad Gateway:         Claude returned an unusable response (AiService)
     *
     * We forward the exception's own status and reason directly into the
     * response — the service layer already chose the right code and message,
     * so there is nothing to translate here.
     *
     * getReason() returns the human-readable message passed to the exception
     * constructor (e.g., "Task not found with id: 5").
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex) {

        ErrorResponse body = new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getReason()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    // -------------------------------------------------------------------------
    // Handler 2: MethodArgumentNotValidException
    // -------------------------------------------------------------------------

    /**
     * Handles validation failures thrown when @Valid rejects a request body.
     *
     * Spring throws MethodArgumentNotValidException when any constraint
     * annotation (@NotBlank, @NotNull, etc.) fails on a @RequestBody parameter.
     * For example, submitting { "title": "" } triggers @NotBlank on TaskRequest.
     *
     * The exception contains a list of FieldError objects — one per failed
     * constraint. We collect them into a single comma-separated message in
     * the format:  "fieldName: error message"
     *
     * Example response body:
     *   { "status": 400, "message": "title: Title is required, dueDate: Due date is required" }
     *
     * This gives the client enough detail to fix all invalid fields in one
     * round trip rather than discovering errors one at a time.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Collect all field-level errors into "field: message" pairs
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                // Prefix each message with its field name for clarity
                .map(msg -> ex.getBindingResult().getFieldErrors().stream()
                        .filter(fe -> fe.getDefaultMessage().equals(msg))
                        .findFirst()
                        .map(fe -> fe.getField() + ": " + msg)
                        .orElse(msg))
                .collect(Collectors.joining(", "));

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message
        );
        return ResponseEntity.badRequest().body(body);
    }

    // -------------------------------------------------------------------------
    // Handler 3: Generic fallback
    // -------------------------------------------------------------------------

    /**
     * Catches any exception not handled by the more specific handlers above.
     *
     * This is a safety net for unexpected runtime errors — NullPointerException,
     * database connection failures, bugs in our own code, etc. We return 500
     * Internal Server Error with a safe generic message.
     *
     * Critically, we never include the exception message or stack trace in the
     * response — internal error details could expose implementation specifics
     * that help an attacker understand the system.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please try again later."
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
