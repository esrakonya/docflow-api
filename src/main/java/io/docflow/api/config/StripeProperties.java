package io.docflow.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "docflow.billing.stripe")
@Getter @Setter @Validated
public class StripeProperties {

    @NotBlank(message = "Stripe Secret Key is required")
    private String secretKey;

    @NotBlank(message = "Stripe Webhook Secret is required")
    private String webhookSecret;

    @NotBlank(message = "Success URL is required")
    private String successUrl;

    @NotBlank(message = "Cancel URL is required")
    private String cancelUrl;
}
