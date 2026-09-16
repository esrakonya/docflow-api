package io.docflow.api.core.billing.service;

import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.ProcessedStripeEvent;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.billing.repository.ProcessedStripeEventRepository;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.service.ClientCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BillingServiceTest {

    @Mock private ApiClientRepository apiClientRepository;
    @Mock private PlanRepository planRepository;
    @Mock private ProcessedStripeEventRepository eventRepository;
    @Mock private ClientCacheService clientCacheService;

    @InjectMocks private BillingService billingService;

    @Test
    @DisplayName("HAPPY PATH: Should upgrade plan and evict cache on successful payment")
    void shouldUpgradePlanOnSuccessfulPayment() throws Exception {
        String eventId = "evt_test_123";
        String stripePriceId = "price_pro_123";
        UUID clientId = UUID.randomUUID();

        ApiClient client = ApiClient.builder()
                .id(clientId)
                .companyName("Test Corp")
                .build();

        Plan proPlan = Plan.builder().name("PRO").monthlyQuota(1000).build();

        Session mockSession = mock(Session.class);
        Price mockPrice = mock(Price.class);
        LineItem mockLineItem = mock(LineItem.class);
        LineItemCollection mockLineItems = mock(LineItemCollection.class);

        when(mockSession.getId()).thenReturn("sess_999");
        when(mockSession.getClientReferenceId()).thenReturn(clientId.toString());
        when(mockSession.getCustomer()).thenReturn("cus_stripe_1");
        when(mockSession.getSubscription()).thenReturn("sub_stripe_1");
        when(mockLineItems.getData()).thenReturn(List.of(mockLineItem));
        when(mockLineItem.getPrice()).thenReturn(mockPrice);
        when(mockPrice.getId()).thenReturn(stripePriceId);
        when(mockSession.getLineItems()).thenReturn(mockLineItems);

        when(eventRepository.existsByEventId(eventId)).thenReturn(false);
        when(apiClientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(planRepository.findByStripePriceId(stripePriceId)).thenReturn(Optional.of(proPlan));

        try (MockedStatic<Session> mockedStripeSession = mockStatic(Session.class)) {
            mockedStripeSession.when(() -> Session.retrieve(eq("sess_999"), any(SessionRetrieveParams.class), any()))
                    .thenReturn(mockSession);

            billingService.fulfillCheckout(eventId, mockSession);
        }

        assertEquals(proPlan, client.getPlan(), "The client plan should be upgraded to PRO!");
        assertEquals("cus_stripe_1", client.getStripeCustomerId(), "Stripe Customer ID should be saved!");

        verify(apiClientRepository).save(client);
        verify(eventRepository).save(any(ProcessedStripeEvent.class));
        verify(clientCacheService).evictAllCacheForClient(clientId);

    }

    @Test
    @DisplayName("IDEMPOTENCY: Should not process the same Stripe event twice")
    void shouldNotProcessDuplicateStripeEvent() throws Exception {
        String eventId = "evt_already_processed";
        when(eventRepository.existsByEventId(eventId)).thenReturn(true);

        billingService.fulfillCheckout(eventId, mock(Session.class));

        verify(apiClientRepository, never()).findById(any());
        verify(clientCacheService, never()).evictAllCacheForClient(any());
    }
}
