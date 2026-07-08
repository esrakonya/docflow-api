package io.docflow.api.core.client.dto;

import java.util.UUID;

public record ClientRegistrationResponse(
        UUID clientId,
        String companyName,
        String rawApiKey,
        String webhookSecret
) { }
