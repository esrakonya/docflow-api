package io.docflow.api.infrastructure.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation error on submitted data.", details);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The file size exceeds the allowed limit (max 10MB per file).", List.of());
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(QuotaExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                ex.getMessage(), List.of("Please upgrade your plan for higher limits."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                ex.getMessage(), List.of("Too many requests in a short period."));
    }

    @ExceptionHandler({RuntimeException.class, Exception.class})
    public ResponseEntity<ErrorResponse> handleAllUncaughtErrors(Exception ex) {
        log.error("UNEXPECTED SYSTEM ERROR: ", ex);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Our engineers are notified.", List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), List.of());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(StorageException ex) {
        log.error("Storage Error: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                "A problem occurred with file storage system.", List.of(ex.getMessage()));
    }

    @ExceptionHandler(SecurityProcessingException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityProcessingException ex) {
        log.error("Security/Hash Error: ",ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "SECURITY_ERROR",
                "Cryptographic operation failed.", List.of());
    }

    @ExceptionHandler(WebhookDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleWebhookException(WebhookDeliveryException ex) {
        log.warn("Webhook Delivery Failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_DELIVERY_FAILED",
                ex.getMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message, List<String> details) {
        ErrorResponse error = new ErrorResponse(
                code,
                message,
                OffsetDateTime.now(),
                details
        );
        return ResponseEntity.status(status).body(error);
    }

}
