package io.docflow.api.core.document.controller;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.service.UsageService;
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
    private final UsageService usageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file")MultipartFile file,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl
    ) {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        usageService.checkAndIncrement(currentClient);

        validateFileType(file);

        String storagePath = storageService.store(file);

        Document doc = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storagePath(storagePath)
                .status(DocumentStatus.PENDING)
                .uploadedAt(OffsetDateTime.now())
                .client(currentClient)
                .callbackUrl(callbackUrl)
                .build();

        Document savedDoc = documentRepository.save(doc);

        kafkaTemplate.send("document-uploaded", new DocumentUploadedEvent(
                savedDoc.getId(), storagePath, file.getContentType()));

        return ResponseEntity.accepted().body(new DocumentResponse(savedDoc.getId(), "PENDING"));
    }

    public record DocumentResponse(UUID id, String status) {}

    public void validateFileType(MultipartFile file) {
        if (file.isEmpty()) throw new RuntimeException("Dosya boş olamaz");
        if (!SUPPORTED_MIME_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("Desteklenmeyen dosya formatı!");
        }
    }

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


