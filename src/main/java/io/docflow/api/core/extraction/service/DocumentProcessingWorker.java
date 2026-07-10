package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.entity.ProcessingAttempt;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.document.repository.ProcessingAttemptRepository;
import io.docflow.api.core.document.service.DocumentInternalService;
import io.docflow.api.core.document.service.WebhookService;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingWorker {

    private final DocumentExtractionService extractionService;
    private final DocumentInternalService documentInternalService;
    private final ProcessingAttemptRepository attemptRepository;
    private final StorageService storageService;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "document-uploaded", groupId = "docflow-group")
    public void processDocument(
            DocumentUploadedEvent event,
            @Header(name = "kafka_deliveryAttempt", defaultValue = "1") int attempt  //Kafka retry count
    ) {
        log.info("Kafka'dan yeni iş alındı: Document ID {}", event.documentId());

        try {
            Document doc = documentInternalService.getByIdWithClient(event.documentId());

            byte[] fileBytes = storageService.fetch(event.storagePath());

            ExtractedInvoiceData result = extractionService.extractAndSave(
                    event.documentId(),
                    fileBytes,
                    event.contentType()
            );

            logAttempt(event.documentId(), "SUCCESS", null, attempt);

            String secret = doc.getClient().getWebhookSecret();

            if (doc.getCallbackUrl() != null && !doc.getCallbackUrl().isBlank()) {
                DocumentWebhookEvent webhookEvent = new DocumentWebhookEvent(
                        doc.getId(),
                        doc.getStatus(),
                        result
                );
                kafkaTemplate.send("webhook-events", webhookEvent);
                log.info("Webhook event sent to queue for document: {}", event.documentId());
            }

            log.info("Process completed and webhook queued: {}", event.documentId());

        } catch (Exception e) {
            log.error("Belge işlenirken hata oluştu! ID: {} - Hata: {}", event.documentId(), attempt);

            logAttempt(event.documentId(), "FAILED", e.getMessage(), attempt);

            throw new RuntimeException(e);
        }
    }


    @DltHandler
    public void handleDlt(DocumentUploadedEvent event) {
        log.error("TÜM DENEMELER BAŞARISIZ! Belge 'FAILED' statüsüne çekiliyor. ID: {}", event.documentId());

        documentInternalService.updateStatus(event.documentId(), DocumentStatus.FAILED);
    }

    private void logAttempt(UUID docId, String status, String error, int attemptNumber) {
        try {
            Document doc = documentInternalService.getById(docId);
            ProcessingAttempt attempt = ProcessingAttempt.builder()
                    .document(doc)
                    .status(status)
                    .errorMessage(error)
                    .attemptedAt(OffsetDateTime.now())
                    .attemptNumber(attemptNumber)
                    .build();
            attemptRepository.save(attempt);
        } catch (Exception e) {
            log.error("Deneme kaydı veritabanına yazılamadı!", e);
        }
    }
}
