package io.docflow.api.core.document.service;

import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookWorker {
    private final WebhookService webhookService;
    private final DocumentInternalService documentInternalService;

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 5000, multiplier = 3.0)
    )
    @KafkaListener(topics = "webhook-events", groupId = "docflow-webhook-group")
    private void handleWebhookEvent(DocumentWebhookEvent event) {
        Document doc = documentInternalService.getById(event.documentId());
        String secret = doc.getClient().getWebhookSecret();

        webhookService.sendCallback(doc.getCallbackUrl(), secret, event);
    }
}
