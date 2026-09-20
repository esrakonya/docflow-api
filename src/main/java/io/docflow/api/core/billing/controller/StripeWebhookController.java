package io.docflow.api.core.billing.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.service.BillingService;
import io.docflow.api.core.billing.service.StripeEventHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeEventHandlerService stripeEventHandlerService;
    private final StripeProperties stripeProperties;

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    stripeProperties.getWebhookSecret()
            );

            log.info("Received Stripe Event: {}", event.getType());

            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElseThrow();
                    stripeEventHandlerService.fulfillCheckout(event.getId(), session);
                    log.info("Checkout fulfilled for session: {}", session.getId());
                }
                case "customer.subscription.deleted" -> {
                    Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElseThrow();
                    stripeEventHandlerService.handleSubscriptionDeleted(event.getId(), subscription);
                }
                case "customer.subscription.updated" -> {
                    Subscription subscription = (Subscription) event.getDataObjectDeserializer().getObject().orElseThrow();
                    stripeEventHandlerService.handleSubscriptionUpdated(event.getId(), subscription);
                }
                case "invoice.payment_failed" -> {
                    Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElseThrow();
                    stripeEventHandlerService.handlePaymentFailed(invoice);
                }
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }

            return ResponseEntity.ok("Event Processed");
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe Signature!");
            return ResponseEntity.status(400).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(500).body("Internal processing error");
        }
    }
}
