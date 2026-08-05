package io.docflow.api.core.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.service.UsageService;
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
import org.apache.kafka.common.errors.ResourceNotFoundException;
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
    private final ApiClientRepository apiClientRepository;
    private final OutboxRepository outboxRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final UsageService usageService;

    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id" + id));
    }

    public Document getByIdWithClient(UUID id) {
        return documentRepository.findByIdWithClient(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id" + id));
    }

    public Page<Document> findAllByClient(ApiClientDto clientDto, Pageable pageable) {
        ApiClient clientEntity = apiClientRepository.getReferenceById(clientDto.getId());
        return documentRepository.findAllByClient(clientEntity, pageable);
    }

    public Optional<Document> findByIdAndClient(UUID id, ApiClientDto clientDto) {
        ApiClient clientEntity = apiClientRepository.getReferenceById(clientDto.getId());
        return documentRepository.findByIdAndClient(id, clientEntity);
    }

    @Transactional
    public void markAsProcessed(UUID id, OffsetDateTime processedAt) {
        Document doc = documentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Document not found: " + id));
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
    public Document uploadSingle(MultipartFile file, String callbackUrl, ApiClientDto clientDto) {
        usageService.checkAndReturnRemaining(clientDto);

        ApiClient clientEntity = apiClientRepository.getReferenceById(clientDto.getId());

        String safeFilename = FileSanitizer.sanitize(file.getOriginalFilename());
        String storagePath = storageService.store(file);

        Document doc = Document.builder()
                .originalFilename(safeFilename)
                .storagePath(storagePath)
                .status(DocumentStatus.PENDING)
                .uploadedAt(OffsetDateTime.now())
                .client(clientEntity)
                .callbackUrl(callbackUrl)
                .build();
        Document savedDoc = documentRepository.save(doc);

        saveToOutbox(savedDoc, storagePath, file.getContentType());

        return savedDoc;
    }

    @Transactional
    public List<Document> uploadBatch(List<MultipartFile> files, String callbackUrl, ApiClientDto clientDto) {
        List<Document> savedDocuments = new ArrayList<>();

        for (MultipartFile file : files) {
            savedDocuments.add(uploadSingle(file, callbackUrl, clientDto));
        }
        return savedDocuments;
    }

    @Transactional
    public void updateStatus(UUID id, DocumentStatus status) {
        Document doc = documentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Document not found: " + id));
        doc.setStatus(status);
        documentRepository.save(doc);
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
