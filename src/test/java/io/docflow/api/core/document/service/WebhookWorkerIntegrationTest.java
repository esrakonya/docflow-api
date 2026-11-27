package io.docflow.api.core.document.service;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.document.dto.DocumentWebhookEvent;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class WebhookWorkerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Gerçek HTTP isteği atmasın diye WebhookService'i taklit (mock) ediyoruz.
    // Bizim test ettiğimiz şey: worker mesajı alıp doğru bilgilerle sendCallback'i çağırıyor mu?

    @MockitoBean
    private WebhookService webhookService;

    @Test
    void shouldDeliverWebhookWhenEventIsConsumed() {
        // TODO 1: Bir ApiClient kaydet. webhookSecret = "test-secret" olsun.
        //         İpucu: FullPipelineIntegrationTest'te client nasıl kaydediliyor, bak.
        //         Plan için getFreePlan() kullanabilirsin.

        ApiClient apiClient = apiClientRepository.save(ApiClient.builder()
                .companyName("Webhook Test Co")
                .plan(getFreePlan())
                .webhookSecret("test-secret")
                .build());

        // TODO 2: Bu client'a ait bir Document kaydet.
        //         callbackUrl = "https://example.com/hook", status = PROCESSED olsun.
        //         İpucu: DocumentService.storeDocument'ta Document.builder() nasıl kullanılmış?

        Document document = documentRepository.save(Document.builder()
                .originalFilename("invoice.pdf")
                .storagePath("test/invoice.pdf")
                .status(DocumentStatus.PROCESSED)
                .callbackUrl("https://example.com/hook")
                .client(apiClient)
                .build());

        // TODO 3: DocumentWebhookEvent oluştur ve kafkaTemplate.send("webhook-events", event) ile gönder.
        DocumentWebhookEvent event = new DocumentWebhookEvent(
                document.getId(),
                DocumentStatus.PROCESSED,
                null
        );

        kafkaTemplate.send("webhook-events", event);

        // TODO 4: Worker asenkron çalıştığı için hemen kontrol edemeyiz, beklememiz gerekiyor.
        //         await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        //             verify(webhookService).sendCallback(
        //                 eq("https://example.com/hook"), eq("test-secret"), any()));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                verify(webhookService).sendCallback(
                        eq("https://example.com/hook"),eq("test-secret"), any()
                ));
    }
}
