package io.docflow.api.core.client.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateApiKeyResponse (
        UUID id,
        String label,
        String rawKey,
        OffsetDateTime createdAt
) {
}
