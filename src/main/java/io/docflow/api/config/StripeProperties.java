package io.docflow.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "docflow.billing.stripe")
@Getter
@Setter
public class StripeProperties {
    private String secretKey;
    private String webhookSecret;
    private String successUrl;
    private String cancelUrl;

    public boolean isConfigured() {
        return hasText(secretKey)
                && hasText(webhookSecret)
                && hasText(successUrl)
                && hasText(cancelUrl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
