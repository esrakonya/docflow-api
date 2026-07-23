package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;

import java.util.Optional;

public interface ClientCacheService {
    Optional<ApiClientDto> getClientByApiKey(String apiKey);
    void evictClientCache(String apiKey);
}
