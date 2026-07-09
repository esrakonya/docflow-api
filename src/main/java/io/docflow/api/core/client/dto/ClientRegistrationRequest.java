package io.docflow.api.core.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRegistrationRequest(
        @NotBlank(message = "Şirket/Hesap adı boş olamaz.")
        @Size(min = 2, max = 100, message = "İsim 2 ile 100 karakter arasında olmalıdır.")
        String name
) { }
