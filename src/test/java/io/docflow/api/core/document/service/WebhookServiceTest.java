package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceTest {

    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks @Spy private WebhookService webhookService;
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicHttpsPort())
            .build();

    @Test
    @DisplayName("DNS Pinning ve HTTPS Akış Testi: Güvenli bir host için tüm mimari aşamaları çalışmalı")
    void shouldExecuteFullDnsPinningLogic() throws Exception {
        // GIVEN
        String host = "trusted-webhook.com";
        String secureUrl = "https://" + host + ":" + wm.getHttpsPort() + "/callback";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        InetAddress realLocalhost = InetAddress.getByName("127.0.0.1");

        wm.stubFor(post(urlEqualTo("/callback"))
                .willReturn(aResponse().withStatus(200)));

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(() -> InetAddress.getAllByName(host))
                    .thenReturn(new InetAddress[]{realLocalhost});

            doReturn(false).when(webhookService).isBlockedAddress(realLocalhost);

            assertDoesNotThrow(() ->
                    webhookService.sendCallback(secureUrl, "secret-key", event)
            );

            wm.verify(postRequestedFor(urlEqualTo("/callback")));
        }
    }

    @Test
    @DisplayName("SSRF Koruması: Yasaklı IP (127.0.0.1) tespiti anında kesilmeli")
    void shouldBlockUnsafeIp() throws Exception {
        String host = "malicious-host.com";
        String url = "http://" + host + "/hack";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        InetAddress realLocalhost = InetAddress.getByName("127.0.0.1");

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(() -> InetAddress.getAllByName(host))
                    .thenReturn(new InetAddress[]{realLocalhost});

            assertThrows(RuntimeException.class, () ->
                    webhookService.sendCallback(url, "secret", event));
        }
    }

    @Test
    @DisplayName("Hata Yönetimi: Bağlantı reddedildiğinde WebhookDeliveryException fırlatmalı")
    void shouldThrowExceptionOnConnectionFailure() throws Exception {
        String unreachableUrl = "https://trusted-host.com:1234/webhook";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        InetAddress realIp = InetAddress.getByName("1.1.1.1");

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {

            mockedInet.when(() -> InetAddress.getAllByName("trusted-host.com"))
                    .thenReturn(new InetAddress[]{realIp});

            assertThrows(WebhookDeliveryException.class, () ->
                    webhookService.sendCallback(unreachableUrl, "secret", event));
        }
    }
}
