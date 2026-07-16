package io.docflow.api.core.extraction;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.core.extraction.dto.ExtractedInvoiceData;
import io.docflow.api.core.extraction.service.DocumentExtractionService;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.Mockito.doAnswer;

@Slf4j
@AutoConfigureMockMvc
@DirtiesContext
class FullPipelineIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ApiClientRepository apiClientRepository;
    @Autowired private io.docflow.api.core.document.service.DocumentInternalService documentInternalService; // EKLENDİ

    @MockitoBean
    private DocumentExtractionService extractionService;

    @Test
    @DisplayName("Uçtan uca akış: Yükleme -> Kafka -> Worker -> DB kontrolü")
    void shouldProcessFullPipelineSuccessfully() throws Exception {
        String apiKey = "pipeline-test-key";
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Pipeline Test Co")
                .apiKeyHash(HashUtils.sha256(apiKey))
                .planTier("pro").monthlyQuota(1000).build());

        ExtractedInvoiceData mockResult = new ExtractedInvoiceData(
                "Mock Store", "M-123", LocalDate.now(), LocalDate.now().plusDays(10),
                "TRY", new BigDecimal("100.00"), BigDecimal.ZERO, List.of(), new BigDecimal("0.99")
        );

        doAnswer(invocation -> {
            UUID docId = invocation.getArgument(0); // Metoda gelen ilk parametreyi (ID) al
            log.info("Mocking: Updating status for document {}", docId);

            documentInternalService.markAsProcessed(docId, java.time.OffsetDateTime.now());

            return mockResult; // Hayali sonucu dön
        }).when(extractionService).extractAndSave(any(), any(), any());

        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf",
                MediaType.APPLICATION_PDF_VALUE, "fake pdf content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .header("X-API-KEY", apiKey))
                .andExpect(status().isAccepted());

        log.info("File uploaded to CI, waiting for async pipeline...");

        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    var docs = documentRepository.findAllByClient(client, Pageable.unpaged());
                    if (docs.isEmpty()) return false;

                    Document doc = docs.getContent().get(0);
                    log.info("Test polling - ID: {} - Status: {}", doc.getId(), doc.getStatus());
                    return doc.getStatus() == DocumentStatus.PROCESSED;
                });

        log.info("Full pipeline integration test successful!");
    }
}