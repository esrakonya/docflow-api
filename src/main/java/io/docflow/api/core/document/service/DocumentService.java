package io.docflow.api.core.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.common.entity.OutboxMessage;
import io.docflow.api.core.common.repository.OutboxRepository;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.storage.service.StorageService;
import io.docflow.api.infrastructure.util.FileSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final OutboxRepository outboxRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }

    public Document getByIdWithClient(UUID id) {
        return documentRepository.findByIdWithClient(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }

    public Page<Document> findAllByClient(ApiClient client, Pageable pageable) {
        return documentRepository.findAllByClient(client, pageable);
    }

    public Optional<Document> findByIdAndClient(UUID id, ApiClient client) {
        return documentRepository.findByIdAndClient(id, client);
    }

    @Transactional
    public void updateStatus(UUID id, DocumentStatus status) {
        Document doc = getById(id);
        doc.setStatus(status);
        documentRepository.save(doc);
    }

    @Transactional
    public void markAsProcessed(UUID id, OffsetDateTime processedAt) {
        Document doc = getById(id);
        doc.setStatus(DocumentStatus.PROCESSED);
        doc.setProcessedAt(processedAt);
        documentRepository.save(doc);
    }

    @Transactional
    public void markAsNeedReview(UUID id) {
        Document doc = getById(id);
        doc.setStatus(DocumentStatus.NEEDS_REVIEW);
        documentRepository.save(doc);
    }

    @Transactional
    public Document uploadSingle(MultipartFile file, String callbackUrl, ApiClient client) {
        String safeFilename = FileSanitizer.sanitize(file.getOriginalFilename());

        String storagePath = storageService.store(file);

        Document doc = Document.builder()
                .originalFilename(safeFilename)
                .storagePath(storagePath)
                .status(DocumentStatus.PENDING)
                .uploadedAt(OffsetDateTime.now())
                .client(client)
                .callbackUrl(callbackUrl)
                .build();
        Document savedDoc = documentRepository.save(doc);

        saveToOutbox(savedDoc, storagePath, file.getContentType());

        return savedDoc;
    }

    @Transactional
    public List<Document> uploadBatch(List<MultipartFile> files, String callbackUrl, ApiClient client) {
        List<Document> savedDocuments = new ArrayList<>();

        for (MultipartFile file : files) {
            savedDocuments.add(uploadSingle(file, callbackUrl, client));
        }
        return savedDocuments;
    }

    private void saveToOutbox(Document doc, String storagePath, String contentType) {
        try {
            DocumentUploadedEvent event = new DocumentUploadedEvent(doc.getId(), storagePath, contentType);
            String payload = objectMapper.writeValueAsString(event);

            outboxRepository.save(OutboxMessage.builder()
                    .topic("document-uploaded")
                    .payload(payload)
                    .createdAt(LocalDateTime.now())
                    .processed(false)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Outbox serialization error for document: {}", doc.getId());
            throw new RuntimeException("Event serialization failed", e);
        }
    }
}
