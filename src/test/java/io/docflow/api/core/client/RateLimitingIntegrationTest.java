package io.docflow.api.core.client;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RateLimitingIntegrationTest extends BaseIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ApiClientRepository apiClientRepository;

    @Test
    @DisplayName("Dakikada izin verilenden fazla istek atıldığında 429 Too Many Requests dönmeli")
    void shouldEnforceRateLimitOnUpload() throws Exception{
        String rawKey = "limit-test-key-123";
        apiClientRepository.save(ApiClient.builder()
                .companyName("Limit Test Co")
                .apiKeyHash(HashUtils.sha256(rawKey))
                .planTier("free").monthlyQuota(100).build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                "test image content".getBytes()
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
