package io.docflow.api.core.document;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.document.entity.Document;
import io.docflow.api.core.document.entity.DocumentStatus;
import io.docflow.api.core.document.repository.DocumentRepository;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;


import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@AutoConfigureMockMvc
public class DocumentIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("Müşteri A, Müşteri B'ye ait döküman ID'si ile sorgu attığında 404 almalıdır")
    void shouldPreventIDORAccess() throws Exception {
        // Create a client A
        String rawKeyA = "key-a-789";
        ApiClient clientA = apiClientRepository.save(ApiClient.builder()
                .companyName("Client A")
                .apiKeyHash(HashUtils.sha256(rawKeyA))
                .build());

        // Create a client B
        ApiClient clientB = apiClientRepository.save(ApiClient.builder()
                .companyName("Client B")
                .apiKeyHash(HashUtils.sha256("key-b-456"))
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
