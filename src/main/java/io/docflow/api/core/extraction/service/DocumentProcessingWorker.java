package io.docflow.api.core.extraction.service;

import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingWorker {

    private final DocumentExtractionService extractionService;

    @KafkaListener(topics = "document-uploaded", groupId = "docflow-group")
    public void processDocument(DocumentUploadedEvent event) {
        log.info("Kafka'dan yeni iş alındı: Document ID {}", event.documentId());

        try {
            byte[] fileBytes = Files.readAllBytes(Path.of(event.storagePath()));

            extractionService.extractAndSave(event.documentId(), fileBytes, event.contentType());

            log.info("İşlem başarıyla tamamlandı: {}", event.documentId());
        } catch (Exception e) {
            log.error("Belge işlenirken hata oluştu! ID: {}", event.documentId(), e);
        }
    }
}
