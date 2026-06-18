package io.docflow.api.infrastructure.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        List<String> details
) { }
