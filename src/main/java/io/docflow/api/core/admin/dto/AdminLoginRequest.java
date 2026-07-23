package io.docflow.api.core.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin giriş isteği için kullanılan kontrat (DTO).
 * Record kullanımı değişmezlik (immutability) ve temiz kod sağlar.
 */
public record AdminLoginRequest(
        @NotBlank(message = "Username cannot be empty")
        String username,

        @NotBlank(message = "Password cannot be empty")
        String password
) {
}
