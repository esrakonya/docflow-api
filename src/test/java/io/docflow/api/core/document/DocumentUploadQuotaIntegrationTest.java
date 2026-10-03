package io.docflow.api.core.document;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.common.repository.OutboxRepository;
import io.docflow.api.infrastructure.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentUploadQuotaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void resetStorageMock() {
        reset(storageService);
    }

    @Test
    void singleUploadConsumesOneCreditAndReturnsReservedRemainingQuota() throws Exception {
        UploadClient client = createClient(5);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();
        when(storageService.store(any())).thenReturn("single-" + UUID.randomUUID() + ".pdf");

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(validPdf("file", "invoice.pdf"))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));

        assertEquals(1, usageFor(client.client()).orElseThrow().getRequestCount());
        assertEquals(initialDocumentCount + 1, documentRepository.count());
        assertEquals(initialOutboxCount + 1, outboxRepository.count());
        verify(storageService, times(1)).store(any());
    }

    @Test
    void batchUploadConsumesExactlyTheBatchSizeAndReturnsReservedRemainingQuota() throws Exception {
        UploadClient client = createClient(10);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();
        when(storageService.store(any()))
                .thenReturn("batch-1-" + UUID.randomUUID() + ".pdf")
                .thenReturn("batch-2-" + UUID.randomUUID() + ".pdf")
                .thenReturn("batch-3-" + UUID.randomUUID() + ".pdf");

        mockMvc.perform(multipart("/api/v1/documents/upload/batch")
                        .file(validPdf("files", "one.pdf"))
                        .file(validPdf("files", "two.pdf"))
                        .file(validPdf("files", "three.pdf"))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "7"));

        assertEquals(3, usageFor(client.client()).orElseThrow().getRequestCount());
        assertEquals(initialDocumentCount + 3, documentRepository.count());
        assertEquals(initialOutboxCount + 3, outboxRepository.count());
        verify(storageService, times(3)).store(any());
    }

    @Test
    void invalidBatchDoesNotReserveQuotaOrStartUpload() throws Exception {
        UploadClient client = createClient(5);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();

        mockMvc.perform(multipart("/api/v1/documents/upload/batch")
                        .file(validPdf("files", "valid.pdf"))
                        .file(new MockMultipartFile("files", "invalid.txt", MediaType.TEXT_PLAIN_VALUE, "not a PDF".getBytes()))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isBadRequest());

        assertTrue(usageFor(client.client()).isEmpty());
        assertEquals(initialDocumentCount, documentRepository.count());
        assertEquals(initialOutboxCount, outboxRepository.count());
        verify(storageService, never()).store(any());
    }

    @Test
    void insufficientBatchQuotaDoesNotStartStorageOrPersistence() throws Exception {
        UploadClient client = createClient(2);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();

        mockMvc.perform(multipart("/api/v1/documents/upload/batch")
                        .file(validPdf("files", "one.pdf"))
                        .file(validPdf("files", "two.pdf"))
                        .file(validPdf("files", "three.pdf"))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isTooManyRequests());

        assertTrue(usageFor(client.client()).isEmpty());
        assertEquals(initialDocumentCount, documentRepository.count());
        assertEquals(initialOutboxCount, outboxRepository.count());
        verify(storageService, never()).store(any());
    }

    @Test
    void singleStorageFailureRollsBackQuotaDocumentAndOutbox() throws Exception {
        UploadClient client = createClient(5);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();
        when(storageService.store(any())).thenThrow(new StorageException("storage unavailable"));

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(validPdf("file", "invoice.pdf"))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isInternalServerError());

        assertTrue(usageFor(client.client()).isEmpty());
        assertEquals(initialDocumentCount, documentRepository.count());
        assertEquals(initialOutboxCount, outboxRepository.count());
        verify(storageService, times(1)).store(any());
    }

    @Test
    void batchStorageFailureRollsBackDatabaseStateAndStopsLaterFiles() throws Exception {
        UploadClient client = createClient(5);
        long initialOutboxCount = outboxRepository.count();
        long initialDocumentCount = documentRepository.count();
        when(storageService.store(any()))
                .thenReturn("first-" + UUID.randomUUID() + ".pdf")
                .thenThrow(new StorageException("storage unavailable"));

        mockMvc.perform(multipart("/api/v1/documents/upload/batch")
                        .file(validPdf("files", "one.pdf"))
                        .file(validPdf("files", "two.pdf"))
                        .file(validPdf("files", "three.pdf"))
                        .header("X-API-KEY", client.apiKey()))
                .andExpect(status().isInternalServerError());

        assertTrue(usageFor(client.client()).isEmpty());
        assertEquals(initialDocumentCount, documentRepository.count());
        assertEquals(initialOutboxCount, outboxRepository.count());
        verify(storageService, times(2)).store(any());
    }

    private UploadClient createClient(int quota) {
        String planName = "UPLOAD_QUOTA_" + UUID.randomUUID();
        Plan plan = planRepository.save(Plan.builder()
                .name(planName)
                .monthlyQuota(quota)
                .rateLimitPerMin(10)
                .build());
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Upload quota test " + UUID.randomUUID())
                .status(ClientStatus.ACTIVE)
                .plan(plan)
                .build());
        String apiKey = "upload-quota-" + UUID.randomUUID();

        when(clientCacheService.getClientByApiKey(apiKey)).thenReturn(Optional.of(ApiClientDto.builder()
                .id(client.getId())
                .companyName(client.getCompanyName())
                .status(ClientStatus.ACTIVE)
                .monthlyQuota(quota)
                .rateLimitPerMin(plan.getRateLimitPerMin())
                .planName(planName)
                .build()));

        return new UploadClient(client, apiKey);
    }

    private Optional<io.docflow.api.core.client.entity.UsageRecord> usageFor(ApiClient client) {
        return usageRecordRepository.findByClientIdAndUsageMonth(client.getId(), currentMonth());
    }

    private MockMultipartFile validPdf(String fieldName, String fileName) {
        return new MockMultipartFile(fieldName, fileName, MediaType.APPLICATION_PDF_VALUE, "%PDF-1.5\n%quota-test".getBytes());
    }

    private String currentMonth() {
        return LocalDate.now().toString().substring(0, 7);
    }

    private record UploadClient(ApiClient client, String apiKey) {
    }
}
