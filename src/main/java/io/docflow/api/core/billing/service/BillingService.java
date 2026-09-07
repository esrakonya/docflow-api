package io.docflow.api.core.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.entity.Plan;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.http.SessionCreationPolicy;
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
                .setSuccessUrl(stripeProperties.getSuccessUrl() + "...")
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

        clientCacheService.evictCacheByHash(client.getApiKeyHash());

        eventRepository.save(ProcessedStripeEvent.builder().eventId(eventId).eventType("checkout.completed").build());

        log.info("Client {} successfully upgraded to plan: {}", clientId, newPlan.getName());
    }
}
