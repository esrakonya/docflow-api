package io.docflow.api.core.document.controller;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.service.RateLimitingService;
import io.docflow.api.core.client.service.UsageService;
import io.docflow.api.core.document.dto.DocumentUploadedEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.mapper.DocumentMapper;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.document.service.DocumentService;
import io.docflow.api.core.extraction.service.DocumentExtractionService;
import io.docflow.api.core.storage.service.StorageService;
import io.docflow.api.infrastructure.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final DocumentService documentService;
    private final UsageService usageService;
    private final RateLimitingService rateLimitingService;
    private final DocumentMapper documentMapper;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file")MultipartFile file,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl
    ) {
        ApiClient currentClient = getCurrentClient();

        rateLimitingService.checkRateLimit(currentClient);
        validateFileType(file);

        int remaining = usageService.checkAndReturnRemaining(currentClient);

        Document savedDoc = documentService.uploadSingle(file, callbackUrl, currentClient);

        return ResponseEntity.accepted()
                .header("X-RateLimit-Limit", String.valueOf(currentClient.getMonthlyQuota()))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(documentMapper.toResponse(savedDoc));
    }

    @PostMapping(value = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentResponse>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl
    ) {
        ApiClient currentClient = getCurrentClient();

        rateLimitingService.checkRateLimit(currentClient);
        files.forEach(this::validateFileType);
        int remaining = usageService.checkAndReturnRemaining(currentClient);

        List<Document> savedDocs = documentService.uploadBatch(files, callbackUrl, currentClient);
        List<DocumentResponse> responses = savedDocs.stream()
                .map(documentMapper::toResponse)
                .toList();

        return ResponseEntity.accepted()
                .header("X-RateLimit-Limit", String.valueOf(currentClient.getMonthlyQuota()))
                .header("X-RateLimit-Remaining", String.valueOf(remaining))
                .body(responses);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> getAllDocuments(Pageable pageable) {

        Page<DocumentResponse> responses = documentService.findAllByClient(getCurrentClient(), pageable)
                .map(documentMapper::toResponse);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentDetail(@PathVariable UUID id) {

        return documentService.findByIdAndClient(id, getCurrentClient())
                .map(documentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private ApiClient getCurrentClient() {
        return (ApiClient) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public void validateFileType(MultipartFile file) {
        if (file.isEmpty()) throw new InvalidRequestException("File cannot be empty");

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new InvalidRequestException("File size exceeds 10MB limit");
        }

        if (!SUPPORTED_MIME_TYPES.contains(file.getContentType())) {
            throw new InvalidRequestException("Unsupported file format: " + file.getContentType());
        }
    }

    public record DocumentResponse(UUID id, String status) {}
}


