package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.document.mapper.DocumentMapper;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ApiClientRepository apiClientRepository;
    private final DocumentMapper documentMapper;
    private final ClientCacheService clientCacheService;

    public ClientRegistrationResponse registerNewClient(String companyName) {
        String rawKey = "invox_live_" + UUID.randomUUID().toString().replace("-", "");
        String hashedkey = HashUtils.sha256(rawKey);

        String webhookSecret = UUID.randomUUID().toString().replace("-", "");

        ApiClient client = ApiClient.builder()
                .companyName(companyName)
                .apiKeyHash(hashedkey)
                .webhookSecret(webhookSecret)
                .planTier("free")
                .monthlyQuota(100)
                .createdAt(OffsetDateTime.now())
                .build();

        ApiClient saved = apiClientRepository.save(client);

        return documentMapper.toRegistrationResponse(saved, rawKey);
    }

    @Transactional
    public void updateClientStatus(UUID clientId, ClientStatus newStatus) {
        ApiClient client = apiClientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        client.setStatus(newStatus);
        apiClientRepository.save(client);

        clientCacheService.evictCacheByHash(client.getApiKeyHash());

        log.info("Client {} status uploaded to {} and cache cleared.", clientId, newStatus);
    }
}
