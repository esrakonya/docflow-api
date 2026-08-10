package io.docflow.api.core.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.infrastructure.exception.WebhookDeliveryException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            .options(wireMockConfig()
                    .dynamicHttpsPort()
                    .keystorePath("src/test/resources/wiremock-keystore.jks")
                    .keystorePassword("password")
                    .keyManagerPassword("password"))
            .build();

    @BeforeAll
    static void setupSslTrust() throws Exception {
        Path trustStoreFile = Paths.get("src/test/resources/wiremock-keystore.jks");

        System.setProperty("javax.net.ssl.trustStore", trustStoreFile.toAbsolutePath().toString());
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStoreType");
    }


    @Test
    @DisplayName("HAPPY PATH: Gerçek DNS Pinning + Gerçek TLS El Sıkışması (System-Level Trust)")
    void shouldDeliverWebhookSuccessfullyWithRealTls() throws Exception {
        String host = "trusted-webhook.com";
        String secureUrl = "https://" + host + ":" + wm.getHttpsPort() + "/callback";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);

        wm.stubFor(post(urlEqualTo("/callback")).willReturn(aResponse().withStatus(200)));

        InetAddress realLocalhost = InetAddress.getByName("127.0.0.1");

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(() -> InetAddress.getAllByName(host))
                    .thenReturn(new InetAddress[]{realLocalhost});

            doReturn(false).when(webhookService).isBlockedAddress(any());

            assertDoesNotThrow(() ->
                    webhookService.sendCallback(secureUrl, "secret-key", event)
            );

            wm.verify(postRequestedFor(urlEqualTo("/callback"))
                    .withHeader("X-DocFlow-Signature", matching(".+")));
        }
    }

    @Test
    @DisplayName("Hata Yönetimi: Kapalı porta bağlantı (Network İzole)")
    void shouldThrowExceptionOnConnectionFailure() throws Exception {
        String unreachableUrl = "https://trusted-host.com:1/webhook";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);
        InetAddress localIp = InetAddress.getByName("127.0.0.1");

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(() -> InetAddress.getAllByName("trusted-host.com"))
                    .thenReturn(new InetAddress[]{localIp});

            assertThrows(WebhookDeliveryException.class, () ->
                    webhookService.sendCallback(unreachableUrl, "secret", event));
        }
    }

    @Test
    @DisplayName("SSRF Koruması: Yasaklı IP (Localhost) tespiti")
    void shouldBlockUnsafeIp() throws Exception {
        String host = "malicious-host.com";
        String url = "http://" + host + "/hack";
        DocumentWebhookEvent event = new DocumentWebhookEvent(UUID.randomUUID(), DocumentStatus.PROCESSED, null);
        InetAddress localIp = InetAddress.getByName("127.0.0.1");

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(() -> InetAddress.getAllByName(host))
                    .thenReturn(new InetAddress[]{localIp});

            assertThrows(RuntimeException.class, () ->
                    webhookService.sendCallback(url, "secret", event));
        }
    }
}
