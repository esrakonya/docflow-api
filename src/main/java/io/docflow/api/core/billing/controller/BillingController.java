package io.docflow.api.core.billing.controller;

import com.stripe.exception.StripeException;
import io.docflow.api.core.billing.service.BillingService;
import io.docflow.api.core.client.dto.ApiClientDto;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /*
     * Creates a Stripe Checkout Session and returns the URL.
    */
    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, String>> createUpgradeSession(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @RequestParam String targetPlan
    ) throws StripeException {
        String checkoutUrl = billingService.createCheckoutSession(currentClient, targetPlan);

        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }
}
