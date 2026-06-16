package io.docflow.api.core.document.controller;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.service.DocumentExtractionService;
import io.docflow.api.core.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import okhttp3.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf"
    );

    private final DocumentExtractionService extractionService;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file")MultipartFile file) {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Dosya boş olamaz");
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_MIME_TYPES.contains(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Geçersiz dosya tipi");
        }

        try {
            String storagePath = storageService.store(file);

            Document doc = Document.builder()
                    .originalFilename(file.getOriginalFilename())
                    .storagePath(storagePath)
                    .status(DocumentStatus.PENDING)
                    .uploadedAt(OffsetDateTime.now())
                    .client(currentClient)
                    .build();
            Document saveDoc = documentRepository.save(doc);

            DocumentUploadedEvent event = new DocumentUploadedEvent(
                    saveDoc.getId(),
                    storagePath,
                    file.getContentType()
            );
            kafkaTemplate.send("document-uploaded", event);

            return ResponseEntity.accepted().body(new DocumentResponse(saveDoc.getId(), "PENDING"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("İşlem sırasında bir hata oluştu: " + e.getMessage());
        }
    }

    public record DocumentResponse(UUID id, String status) {}

    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments() {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return ResponseEntity.ok(documentRepository.findAllByClient(currentClient));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentDetail(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}


