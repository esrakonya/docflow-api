package io.docflow.api.core.document.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentUploadedEvent(
        UUID documentId,
        String storagePath,
        String contentType
) { }
