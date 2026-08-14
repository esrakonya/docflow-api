package io.docflow.api.core.client;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.infrastructure.exception.RateLimitExceededException;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Rate Limiting mechanism.
 * Validates that the system correctly identifies and blocks excessive requests
 * within a specific time window, returning HTTP 429 (Too Many Requests).
 */
@AutoConfigureMockMvc
class RateLimitingIntegrationTest extends BaseIntegrationTest {
    @Test
    @DisplayName("Should return 429 Too Many Requests when rate limit is exceeded")
    void shouldEnforceRateLimitOnUpload() throws Exception{
        String rawKey = "limit-test-key-123";

        when(storageService.store(any())).thenReturn("uploads/test-path.pdf");

        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Limit Test Corporation")
                .apiKeyHash(HashUtils.sha256(rawKey))
                .status(ClientStatus.ACTIVE)
                .planTier("free")
                .monthlyQuota(100)
                .build());

        when (clientCacheService.getClientByApiKey(rawKey))
                .thenReturn(Optional.of(ApiClientDto.builder()
                        .id(client.getId())
                        .companyName(client.getCompanyName())
                        .status(ClientStatus.ACTIVE)
                        .monthlyQuota(100)
                        .planTier("free")
                        .build()));


        doNothing()
                .doNothing()
                .doNothing()
                .doThrow(new RateLimitExceededException("Rate limit exceeded"))
                .when(rateLimitingService).checkRateLimit(any(ApiClientDto.class));

        byte[] pdfBytes = "%PDF-1.5\n%test".getBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.IMAGE_PNG_VALUE,
                pdfBytes
        );


        for (int i = 0; i < 3; i++) {
            mockMvc.perform(multipart("/api/v1/documents/upload")
                    .file(file)
                    .header("X-API-KEY", rawKey))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("X-API-KEY", rawKey))
                .andExpect(status().isTooManyRequests());
    }
}
