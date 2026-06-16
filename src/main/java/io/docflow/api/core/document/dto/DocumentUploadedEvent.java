package io.docflow.api.core.document.dto;

import java.util.UUID;

public record DocumentUploadedEvent(
        UUID documentId,
        String storagePath,
        String contentType
) { }
