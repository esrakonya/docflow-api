package io.docflow.api.core.client.dto;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record ClientUsageResponse(
        String planName,
        int monthlyLimit,
        int usedCredits,
        int remainingCredits,
        int rateLimitPerMinute
) implements Serializable {}
