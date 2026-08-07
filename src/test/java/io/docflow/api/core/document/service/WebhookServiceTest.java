package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceTest {

    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private WebhookService webhookService;

    @Test
    @DisplayName("SSRF Koruması: Yasaklı bir IP/Host (localhost) girildiğinde RuntimeException fırlatmalı")
    void shouldBlockUnsafeUrl() {
        String unsafeUrl = "http://127.0.0.1/callback";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        assertThrows(RuntimeException.class, () -> webhookService.sendCallback(unsafeUrl, "secret", event));
    }

    @Test
    @DisplayName("Webhook iletimi başarısız olduğunda (Connection Refused vb.) WebhookDeliveryException fırlatmalı")
    void shouldThrowExceptionOneDeliveryFailure() {
        String unreachableUrl = "http://1.1.1.1:1234/webhook";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        assertThrows(WebhookDeliveryException.class, () -> webhookService.sendCallback(unreachableUrl, "secret", event));
    }
}
