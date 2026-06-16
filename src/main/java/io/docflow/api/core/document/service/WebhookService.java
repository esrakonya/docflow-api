package io.docflow.api.core.document.service;

import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class WebhookService {

    private final RestClient restClient = RestClient.create();

    public void sendCallback(String callbackUrl, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        try {
            log.info("Webhook gönderiliyor: {} -> {}", callbackUrl, event.documentId());

            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook başarıyla ulaştı.");
        } catch (Exception e) {
            log.error("Webhook gönderilemedi! Hedef URL: {}", callbackUrl, e);
            // Could be Retry mechanism
        }
    }
}
