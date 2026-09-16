package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;

import java.util.Optional;
import java.util.UUID;

public interface ClientCacheService {
    Optional<ApiClientDto> getClientByApiKey(String apiKey);
    void evictClientCache(String apiKey);
    void evictCacheByHash(String apiKeyHash);
    void evictAllCacheForClient(UUID clientId);
}
