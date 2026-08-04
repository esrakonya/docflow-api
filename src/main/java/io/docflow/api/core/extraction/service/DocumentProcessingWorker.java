package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.entity.ProcessingAttempt;
import io.docflow.api.core.document.repository.ProcessingAttemptRepository;
import io.docflow.api.core.document.service.DocumentService;
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

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingWorker {

    private final DocumentExtractionService extractionService;
    private final DocumentService documentService;
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
        log.info("New job received from Kafka: Document ID {}", event.documentId());

        try {
            Document doc = documentService.getByIdWithClient(event.documentId());

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
            log.error("Error processing document! ID: {} - Error: {}", event.documentId(), attempt);

            logAttempt(event.documentId(), "FAILED", e.getMessage(), attempt);

            throw new RuntimeException(e);
        }
    }


    @DltHandler
    public void handleDlt(DocumentUploadedEvent event) {
        log.error("ALL RETRIES FAILED! Marking document as FAILED. ID: {}", event.documentId());

        documentService.updateStatus(event.documentId(), DocumentStatus.FAILED);
    }

    private void logAttempt(UUID docId, String status, String error, int attemptNumber) {
        try {
            Document doc = documentService.getById(docId);
            ProcessingAttempt attempt = ProcessingAttempt.builder()
                    .document(doc)
                    .status(status)
                    .errorMessage(error)
                    .attemptedAt(OffsetDateTime.now())
                    .attemptNumber(attemptNumber)
                    .build();
            attemptRepository.save(attempt);
        } catch (Exception e) {
            log.error("The trial record could not be written to the database!", e);
        }
    }
}
