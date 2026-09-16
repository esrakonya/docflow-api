package io.docflow.api.core.client.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiKeySummaryResponse (
        UUID id,
        String label,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) { }
