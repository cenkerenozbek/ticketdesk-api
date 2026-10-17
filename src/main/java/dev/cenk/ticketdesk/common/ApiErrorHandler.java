package dev.cenk.ticketdesk.common;

import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);
    public record ApiError(int status, String code, String message, Map<String, String> fields) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> domain(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.status().value(), ex.code(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(400, "VALIDATION_FAILED", "Invalid request", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError(400, "INVALID_REQUEST", "Malformed JSON or invalid parameter", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        if (ex instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(new ApiError(
                    error.getStatusCode().value(), "HTTP_ERROR", "Request could not be processed", Map.of()));
        }
        log.error("Unexpected API failure", ex);
        return ResponseEntity.internalServerError().body(new ApiError(500, "INTERNAL_ERROR", "Unexpected server error", Map.of()));
    }
}
