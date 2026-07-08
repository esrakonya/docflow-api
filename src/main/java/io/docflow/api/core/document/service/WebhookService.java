package io.docflow.api.core.document.service;

import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

@Service
@Slf4j
public class WebhookService {

    private final RestClient restClient = RestClient.create();

    private boolean isUrlAllowed(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost().toLowerCase();
            String scheme = uri.getScheme().toLowerCase();

            if (!"http".equals(scheme) && !"https".equals(scheme)) return false;

            List<String> blackListedHost = List.of("localhost", "127.0.0.1", "0.0.0.0");
            if (blackListedHost.contains(host)) return false;

            if (host.equals("169.254.169.254")) return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void sendCallback(String callbackUrl, String secret, DocumentWebhookEvent event) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        if (!isUrlAllowed(callbackUrl)) {
            log.warn("GÜVENLİ OLMAYAN WEBHOOK ADRESİ ENGELLENDİ: {}", callbackUrl);
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
