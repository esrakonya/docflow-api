package io.docflow.api.core.document;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;


import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@AutoConfigureMockMvc
public class DocumentIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Müşteri A, Müşteri B'ye ait döküman ID'si ile sorgu attığında 404 almalıdır")
    void shouldPreventIDORAccess() throws Exception {
        // Create a client A
        String rawKeyA = "key-a-789";
        ApiClient clientA = apiClientRepository.save(ApiClient.builder()
                .companyName("Client A")
                .status(ClientStatus.ACTIVE)
                .remainingQuota(100)
                .monthlyQuota(100)
                .planTier("free")
                .apiKeyHash(HashUtils.sha256(rawKeyA))
                .build());

        when(clientCacheService.getClientByApiKey(rawKeyA))
                .thenReturn(Optional.of(ApiClientDto.builder()
                        .id(clientA.getId())
                        .companyName(clientA.getCompanyName())
                        .status(ClientStatus.ACTIVE)
                        .monthlyQuota(100)
                        .planTier("free")
                        .build()));

        // Create a client B
        ApiClient clientB = apiClientRepository.save(ApiClient.builder()
                .companyName("Client B")
                .apiKeyHash(HashUtils.sha256("key-b-456"))
                .status(ClientStatus.ACTIVE)
                .remainingQuota(100)
                .build());

        Document secretDocB = documentRepository.save(Document.builder()
                .originalFilename("secret-b.pdf")
                .storagePath("/path/to/b")
                .status(DocumentStatus.PENDING)
                .client(clientB)
                .uploadedAt(OffsetDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/documents/" + secretDocB.getId())
                .header("X-API-KEY", rawKeyA))
                .andExpect(status().isNotFound());
    }
}
