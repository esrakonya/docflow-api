package io.docflow.api.core.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.entity.ProcessedStripeEvent;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.billing.repository.ProcessedStripeEventRepository;
import io.docflow.api.core.client.dto.ApiClientDto;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final ApiClientRepository apiClientRepository;
    private final PlanRepository planRepository;
    private final ProcessedStripeEventRepository eventRepository;
    private final ClientCacheService clientCacheService;

    private final StripeProperties stripeProperties;

    public String createCheckoutSession(ApiClientDto clientDto, String targetPlanName) throws StripeException {
        Plan plan = planRepository.findByName(targetPlanName)
                .orElseThrow(() -> new PaymentProcessingException("Plan not found: " + targetPlanName));

        if (plan.getStripePriceId() == null) {
            throw new PaymentProcessingException("This plan is not available for purchase.");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(stripeProperties.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(stripeProperties.getCancelUrl())
                .setClientReferenceId(clientDto.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(plan.getStripePriceId())
                        .setQuantity(1L)
                        .build())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

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
            log.warn("subscription.updated referenced unknown price {} (subscription {})", priceId, subscription.getId());
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

    public String createPortalSession(ApiClientDto clientDto) throws StripeException {
        ApiClient client = apiClientRepository.findById(clientDto.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientDto.getId()));

        if (client.getStripeCustomerId() == null) {
            throw new PaymentProcessingException("No billing account found. Subscribe to a paid plan first.");
        }

        com.stripe.param.billingportal.SessionCreateParams params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(client.getStripeCustomerId())
                .setReturnUrl(stripeProperties.getCancelUrl())
                .build();

        com.stripe.model.billingportal.Session portalSession = com.stripe.model.billingportal.Session.create(params);

        return portalSession.getUrl();
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
        client.setStripeSubscriptionId(null); //keep stripeCustomerId
        apiClientRepository.save(client);

        clientCacheService.evictAllCacheForClient(client.getId());

        eventRepository.save(ProcessedStripeEvent.builder().eventId(eventId).eventType(eventType).build());

        log.info("Client {} downgraded to FREE (reason: {})", client.getId(), eventType);
    }


}
