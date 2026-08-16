package io.docflow.api.infrastructure.exception;

public class SecurityProcessingException extends RuntimeException {
    public SecurityProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
