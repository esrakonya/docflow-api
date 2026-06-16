package io.docflow.api.core.document.dto;

import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;

import java.util.UUID;

public record DocumentWebhookEvent(
        UUID documentId,
        DocumentStatus status,
        ExtractedInvoiceData result
) { }
