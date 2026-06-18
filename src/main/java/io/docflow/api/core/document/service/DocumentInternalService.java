package io.docflow.api.core.document.service;

import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentInternalService {

    private final DocumentRepository documentRepository;

    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
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
}
