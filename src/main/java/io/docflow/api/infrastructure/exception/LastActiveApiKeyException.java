package io.docflow.api.infrastructure.exception;

public class LastActiveApiKeyException extends RuntimeException {
    public LastActiveApiKeyException(String message) {
        super(message);
    }
}
