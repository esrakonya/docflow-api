package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiKeySummaryResponse;
import io.docflow.api.core.client.dto.CreateApiKeyRequest;
import io.docflow.api.core.client.dto.CreateApiKeyResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ApiKey;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.ApiKeyRepository;
import io.docflow.api.infrastructure.exception.LastActiveApiKeyException;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import io.docflow.api.infrastructure.util.ApiKeyGenerator;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiClientRepository apiClientRepository;
    private final ClientCacheService clientCacheService;

    @Transactional
    public CreateApiKeyResponse createKey(UUID clientId, CreateApiKeyRequest request) {
        ApiClient client = apiClientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        String rawKey = ApiKeyGenerator.generateRawKey();
        String hashedKey = HashUtils.sha256(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .client(client)
                .keyHash(hashedKey)
                .label(request.label())
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("New API key '{}' issued for client {}", saved.getLabel(), client);

        return new CreateApiKeyResponse(saved.getId(), saved.getLabel(), rawKey, saved.getCreatedAt());
    }

    public List<ApiKeySummaryResponse> listKeys(UUID clientId) {
        return apiKeyRepository.findAllByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(key -> new ApiKeySummaryResponse(
                        key.getId(), key.getLabel(), key.isActive(), key.getCreatedAt(), key.getExpiresAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeKey(UUID clientId, UUID keyId) {
        ApiKey key = apiKeyRepository.findByIdAndClientId(keyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", "id", keyId));

        if (key.isActive()) {
            long activeKeyCount = apiKeyRepository.findAllByClientIdAndActiveTrue(clientId).size();
            if (activeKeyCount <= 1) {
                throw new LastActiveApiKeyException("Cannot revoke your only active API key. Create a new key first, then revoke this one.");
            }
        }

        key.setActive(false);
        apiKeyRepository.save(key);

        clientCacheService.evictCacheByHash(key.getKeyHash());

        log.info("API key {} ('{}') revoked for client {}", keyId, key.getLabel(), clientId);
    }
}
