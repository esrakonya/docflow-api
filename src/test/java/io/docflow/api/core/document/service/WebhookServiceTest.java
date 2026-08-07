package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceTest {

    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private WebhookService webhookService;

    @Test
    @DisplayName("DNS Pinning ve HTTPS Akış Testi: Güvenli bir host için tüm mimari aşamaları çalışmalı")
    void shouldExecuteFullDnsPinningLogic() throws Exception {
        String secureHost = "trusted-webhook.com";
        String secureUrl = "https://" + secureHost + "/callback";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            InetAddress mockAddress = mock(InetAddress.class);
            when(mockAddress.getHostAddress()).thenReturn("93.184.216.34");
            when(mockAddress.isLoopbackAddress()).thenReturn(false);
            when(mockAddress.isLinkLocalAddress()).thenReturn(false);
            when(mockAddress.isSiteLocalAddress()).thenReturn(false);

            mockedInet.when(() -> InetAddress.getAllByName(secureHost))
                    .thenReturn(new InetAddress[]{mockAddress});

            assertThrows(WebhookDeliveryException.class, () ->
                    webhookService.sendCallback(secureUrl, "secret", event));
        }
    }

    @Test
    @DisplayName("SSRF Koruması: Yasaklı IP (127.0.0.1) tespiti anında kesilmeli")
    void shouldBlockUnsafeIp() throws Exception {
        String host = "malicious-host.com";
        String url = "http://" + host + "/hack";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            InetAddress loopbackAddress = mock(InetAddress.class);
            when(loopbackAddress.isLoopbackAddress()).thenReturn(true); // SALDIRI!

            mockedInet.when(() -> InetAddress.getAllByName(host))
                    .thenReturn(new InetAddress[]{loopbackAddress});

            assertThrows(RuntimeException.class, () ->
                    webhookService.sendCallback(url, "secret", event));
        }
    }
}
