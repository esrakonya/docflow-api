package io.docflow.api.core.document.service;

import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class WebhookService {

    private final RestClient restClient = RestClient.create();

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        try {
            String signature = HashUtils.hmacSha256(event.documentId().toString(), secret);
            log.info("Webhook gönderiliyor: {} -> {}", callbackUrl, event.documentId());

            restClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Invox-Signature", signature)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Webhook başarıyla ulaştı. İmza: {}", signature);
        } catch (Exception e) {
            log.error("Webhook gönderilemedi! Hedef URL: {}", callbackUrl, e);
            // Could be Retry mechanism
        }
    }
}
