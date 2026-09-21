package io.docflow.api.web.dashboard;

import jakarta.validation.constraints.NotBlank;

public record DashboardLoginRequest(
        @NotBlank(message = "API key is required")
        String apiKey
) {
}
