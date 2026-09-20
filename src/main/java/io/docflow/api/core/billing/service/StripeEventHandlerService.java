package io.docflow.api.core.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.entity.ProcessedStripeEvent;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.billing.repository.ProcessedStripeEventRepository;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.service.ClientCacheService;
import io.docflow.api.infrastructure.exception.PaymentProcessingException;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reconciles what Stripe reports back to us via webhook events: completed
 * checkouts, cancelled/updated subscriptions, and failed payments. Starting
 * a checkout or portal session (the outbound direction) lives in
 * {@link BillingService} instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeEventHandlerService {

    private final ApiClientRepository apiClientRepository;
    private final PlanRepository planRepository;
    private final ProcessedStripeEventRepository eventRepository;
    private final ClientCacheService clientCacheService;

    @Transactional
    public void fulfillCheckout(String eventId, Session session) throws StripeException {
        if (eventRepository.existsByEventId(eventId)) {
            log.info("Stripe event {} already processed. Skipping.", eventId);
            return;
        }

        Session retrievedSession = Session.retrieve(session.getId(),
                SessionRetrieveParams.builder().addExpand("line_items").build(), null);

        String priceId = retrievedSession.getLineItems().getData().get(0).getPrice().getId();
        UUID clientId = UUID.fromString(retrievedSession.getClientReferenceId());

        ApiClient client = apiClientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        Plan newPlan = planRepository.findByStripePriceId(priceId)
                .orElseThrow(() -> new PaymentProcessingException("Plan not found for price: " + priceId));

        client.setPlan(newPlan);
        client.setStripeCustomerId(retrievedSession.getCustomer());
        client.setStripeSubscriptionId(retrievedSession.getSubscription());
        apiClientRepository.save(client);

        clientCacheService.evictAllCacheForClient(client.getId());

        eventRepository.save(ProcessedStripeEvent.builder().eventId(eventId).eventType("checkout.completed").build());

        log.info("Client {} successfully upgraded to plan: {}", clientId, newPlan.getName());
    }

    @Transactional
    public void handleSubscriptionDeleted(String eventId, Subscription subscription) {
        downgradeToFree(eventId, "customer.subscription.deleted", subscription.getId());
    }

    @Transactional
    public void handleSubscriptionUpdated(String eventId, Subscription subscription) {
        if (eventRepository.existsByEventId(eventId)) {
            log.info("Stripe event {} already processed. Skipping.", eventId);
            return;
        }

        String status = subscription.getStatus();
        if ("canceled".equals(status) || "unpaid".equals(status) || "incomplete_expired".equals(status)) {
            downgradeToFree(eventId, "customer.subscription.updated", subscription.getId());
            return;
        }

        String priceId = subscription.getItems().getData().get(0).getPrice().getId();

        ApiClient client = apiClientRepository.findByStripeSubscriptionId(subscription.getId())
                .orElse(null);

        if (client == null) {
            log.warn("Received subscription.updated for unknown subscription: {}", subscription.getId());
            return;
        }

        Plan newPlan = planRepository.findByStripePriceId(priceId).orElse(null);
        if (newPlan == null) {
            log.warn("Subscription.updated referenced unknown price {} (subscription {})", priceId, subscription.getId());
            return;
        }

        if (!newPlan.getId().equals(client.getPlan().getId())) {
            client.setPlan(newPlan);
            apiClientRepository.save(client);
            clientCacheService.evictAllCacheForClient(client.getId());
            log.info("Client {} plan synced to {} via subscription.updated", client.getId(), newPlan.getName());
        }

        eventRepository.save(ProcessedStripeEvent.builder()
                .eventId(eventId).eventType("customer.subscription.updated").build());
    }

    public void handlePaymentFailed(Invoice invoice) {
        log.warn("Payment failed for Stripe customer {} (subscription {}). Access not revoked yet; " +
                "waiting for Stripe's dunning process to resolve or cancel the subscription.",
                invoice.getCustomer(), invoice.getSubscription());
    }

    private void downgradeToFree(String eventId, String eventType, String stripeSubscriptionId) {
        if (eventRepository.existsByEventId(eventId)) {
            log.info("Stripe event {} already processed. Skipping.", eventId);
            return;
        }

        ApiClient client = apiClientRepository.findByStripeSubscriptionId(stripeSubscriptionId).orElse(null);
        if (client == null) {
            log.warn("Received {} for unknown subscription: {}", eventType, stripeSubscriptionId);
            return;
        }

        Plan freePlan = planRepository.findByName(PlanTier.FREE)
                .orElseThrow(() -> new PaymentProcessingException("System configuration error: Default subscription tier missing."));

        client.setPlan(freePlan);
        client.setStripeSubscriptionId(null);
        apiClientRepository.save(client);

        clientCacheService.evictAllCacheForClient(client.getId());

        eventRepository.save(ProcessedStripeEvent.builder().eventId(eventId).eventType(eventType).build());

        log.info("Client {} downgraded to FREE (reason: {})", client.getId(), eventType);
    }
}
