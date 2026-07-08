package io.docflow.api.core.document.controller;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.service.RateLimitingService;
import io.docflow.api.core.client.service.UsageService;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.mapper.DocumentMapper;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.service.DocumentExtractionService;
import io.docflow.api.core.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final RateLimitingService rateLimitingService;
    private final DocumentMapper documentMapper;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file")MultipartFile file,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl
    ) {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        rateLimitingService.checkRateLimit(currentClient);

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

        return ResponseEntity.accepted().body(documentMapper.toResponse(savedDoc));
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentResponse>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl
    ) {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        List<DocumentResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            validateFileType(file);
            rateLimitingService.checkRateLimit(currentClient);
            usageService.checkAndIncrement(currentClient);

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
                    savedDoc.getId(), storagePath, file.getContentType()
            ));

            responses.add(new DocumentResponse(savedDoc.getId(), "PENDING"));
        }

        return ResponseEntity.accepted().body(responses);
    }

    public record DocumentResponse(UUID id, String status) {}

    public void validateFileType(MultipartFile file) {
        if (file.isEmpty()) throw new RuntimeException("Dosya boş olamaz");

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("Dosya boyutu çok büyük! Maksimum 10MB yükleyebilirsiniz.");
        }

        if (!SUPPORTED_MIME_TYPES.contains(file.getContentType())) {
            throw new RuntimeException("Desteklenmeyen dosya formatı!");
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        List<DocumentResponse> responses = documentRepository.findAllByClient(currentClient)
                .stream()
                .map(documentMapper::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentDetail(@PathVariable UUID id) {
        ApiClient currentClient = (ApiClient) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        return documentRepository.findByIdAndClient(id, currentClient)
                .map(documentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}


