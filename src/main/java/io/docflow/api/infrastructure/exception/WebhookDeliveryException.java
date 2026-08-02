package io.docflow.api.infrastructure.exception;

public class WebhookDeliveryException extends RuntimeException {
    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
