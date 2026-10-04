package io.docflow.api.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties stripeProperties;

    @PostConstruct
    public void init() {
        if (stripeProperties.isConfigured()) {
            Stripe.apiKey = stripeProperties.getSecretKey();
        }
    }
}
