package io.docflow.api.core.billing.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.infrastructure.exception.PaymentProcessingException;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BillingServiceTest {

    @Mock private ApiClientRepository apiClientRepository;
    @Mock private PlanRepository planRepository;
    @Mock private StripeProperties stripeProperties;
    @InjectMocks private BillingService billingService;

    @Test
    @DisplayName("createCheckoutSession: returns the Stripe-hosted checkout URL for a valid, purchasable plan")
    void shouldCreateCheckoutSessionForValidPlan() throws StripeException {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).build();

        Plan proPlan = Plan.builder().name("PRO").stripePriceId("price_pro_123").build();
        when(planRepository.findByName("PRO")).thenReturn(Optional.of(proPlan));

        when(stripeProperties.getSuccessUrl()).thenReturn("https://app.example.com/success");
        when(stripeProperties.getCancelUrl()).thenReturn("https://app.example.com/cancel");

        Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123");

        try (MockedStatic<Session> mockedSession = mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            String url = billingService.createCheckoutSession(clientDto, "PRO");

            assertEquals("https://checkout.stripe.com/pay/cs_test_123", url);
        }
    }

    @Test
    @DisplayName("createCheckoutSession: rejects an unknown plan name")
    void shouldRejectUnknownPlan() {
        ApiClientDto clientDto = ApiClientDto.builder().id(UUID.randomUUID()).build();
        when(planRepository.findByName("GALAXY")).thenReturn(Optional.empty());

        assertThrows(PaymentProcessingException.class,
                () -> billingService.createCheckoutSession(clientDto, "GALAXY"));
    }

    @Test
    @DisplayName("createCheckoutSession: rejects a plan that has no Stripe price configured")
    void shouldRejectPlanWithoutStripePrice() {
        ApiClientDto clientDto = ApiClientDto.builder().id(UUID.randomUUID()).build();
        Plan freePlan = Plan.builder().name("FREE").stripePriceId(null).build();
        when(planRepository.findByName("FREE")).thenReturn(Optional.of(freePlan));

        assertThrows(PaymentProcessingException.class,
                () -> billingService.createCheckoutSession(clientDto, "FREE"));
    }

    // ---------- createPortalSession ----------

    @Test
    @DisplayName("createPortalSession: returns the Stripe-hosted portal URL for an existing paying customer")
    void shouldCreatePortalSessionForExistingCustomer() throws StripeException {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).build();

        ApiClient client = ApiClient.builder()
                .id(clientId)
                .stripeCustomerId("cus_stripe_1")
                .build();
        when(apiClientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(stripeProperties.getCancelUrl()).thenReturn("https://app.example.com/cancel");

        com.stripe.model.billingportal.Session mockPortalSession = mock(com.stripe.model.billingportal.Session.class);
        when(mockPortalSession.getUrl()).thenReturn("https://billing.stripe.com/session/xyz");

        try (MockedStatic<com.stripe.model.billingportal.Session> mockedPortal =
                     mockStatic(com.stripe.model.billingportal.Session.class)) {
            mockedPortal.when(() -> com.stripe.model.billingportal.Session.create(
                    any(com.stripe.param.billingportal.SessionCreateParams.class))).thenReturn(mockPortalSession);

            String url = billingService.createPortalSession(clientDto);

            assertEquals("https://billing.stripe.com/session/xyz", url);
        }
    }

    @Test
    @DisplayName("createPortalSession: 404s when the client itself no longer exists")
    void shouldThrowWhenClientNotFound() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).build();
        when(apiClientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> billingService.createPortalSession(clientDto));
    }

    @Test
    @DisplayName("createPortalSession: rejects a client who has never subscribed to a paid plan")
    void shouldRejectClientWithoutStripeCustomerId() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).build();

        ApiClient client = ApiClient.builder().id(clientId).stripeCustomerId(null).build();
        when(apiClientRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThrows(PaymentProcessingException.class,
                () -> billingService.createPortalSession(clientDto));
    }

}
