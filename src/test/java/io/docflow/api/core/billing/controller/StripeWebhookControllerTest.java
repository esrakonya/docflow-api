package io.docflow.api.core.billing.controller;

import io.docflow.api.config.StripeProperties;
import io.docflow.api.core.billing.service.StripeEventHandlerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripeEventHandlerService stripeEventHandlerService;

    @Mock
    private StripeProperties stripeProperties;

    @InjectMocks
    private StripeWebhookController stripeWebhookController;

    @Test
    void shouldReturn503WhenStripeIsNotConfigured() {
        when(stripeProperties.isConfigured()).thenReturn(false);

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(
                "{}",
                "test-signature"
        );

        assertEquals(503, response.getStatusCode().value());
        assertEquals("Stripe billing is not configured.", response.getBody());

        verifyNoInteractions(stripeEventHandlerService);
        verify(stripeProperties).isConfigured();
        verifyNoMoreInteractions(stripeProperties);
    }
}
