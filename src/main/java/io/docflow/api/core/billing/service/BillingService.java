package io.docflow.api.core.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.entity.Plan;
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

/**
 * Outbound billing actions triggered by the client: starting a checkout
 * session to upgrade a plan, or opening the Stripe Customer Portal.
 * Reconciling what Stripe reports back (webhook events) lives in
 * {@link StripeEventHandlerService} instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final ApiClientRepository apiClientRepository;
    private final PlanRepository planRepository;
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
}
