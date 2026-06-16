package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.entity.ProcessingAttempt;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.document.repository.ProcessingAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
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
    private final DocumentRepository documentRepository;
    private final ProcessingAttemptRepository attemptRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "document-uploaded", groupId = "docflow-group")
    public void processDocument(DocumentUploadedEvent event) {
        log.info("Kafka'dan yeni iş alındı: Document ID {}", event.documentId());

        try {
            byte[] fileBytes = Files.readAllBytes(Path.of(event.storagePath()));
            extractionService.extractAndSave(event.documentId(), fileBytes, event.contentType());

            logAttempt(event.documentId(), "SUCCESS", null);
        } catch (Exception e) {
            logAttempt(event.documentId(), "FAILED", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @DltHandler
    public void handleDlt(DocumentUploadedEvent event) {
        log.error("TÜM DENEMELER BAŞARISIZ! Belge 'FAILED' statüsüne çekiliyor. ID: {}", event.documentId());

        Document doc = documentRepository.findById(event.documentId()).orElseThrow();
        doc.setStatus(DocumentStatus.FAILED);
        documentRepository.save(doc);
    }

    private void logAttempt(UUID docId, String status, String error) {
        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) return;

        ProcessingAttempt attempt = ProcessingAttempt.builder()
                .document(doc)
                .status(status)
                .errorMessage(error)
                .attemptedAt(OffsetDateTime.now())
                .attemptNumber(0)
                .build();
        attemptRepository.save(attempt);
    }
}
