package io.docflow.api.core.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
        @NotBlank(message = "Username cannot be empty")
        String username,

        @NotBlank(message = "Password cannot be empty")
        String password
) {
}
