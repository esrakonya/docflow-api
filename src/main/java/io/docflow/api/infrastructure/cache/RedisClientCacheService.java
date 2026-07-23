package io.docflow.api.infrastructure.cache;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.service.ClientCacheService;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisClientCacheService implements ClientCacheService {

    private final ApiClientRepository apiClientRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "api_key";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    @Override
    public Optional<ApiClientDto> getClientByApiKey(String apiKey) {
        String hashedKey = HashUtils.sha256(apiKey);
        String cacheKey = CACHE_KEY_PREFIX + hashedKey;

        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                log.debug("Cache hit for API Key: {}", hashedKey);
                return Optional.of((ApiClientDto) cachedData);
            }
        } catch (Exception e) {
            log.error("Redis error, falling back to database: {}", e.getMessage());
        }

        return apiClientRepository.findByApiKeyHash(hashedKey)
                .map(client -> {
                    ApiClientDto dto = mapToDto(client);

                    redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);

                    log.debug("Cache miss. Client loaded from DB and cached: {}", hashedKey);
                    return dto;
                });
    }

    @Override
    public void evictClientCache(String apiKey) {
        redisTemplate.delete(CACHE_KEY_PREFIX + HashUtils.sha256(apiKey));
    }

    private ApiClientDto mapToDto(ApiClient entity) {
        return ApiClientDto.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .apiKeyHash(entity.getApiKeyHash())
                .status(entity.getStatus())
                .remainingQuota(entity.getRemainingQuota())
                .build();
    }
}
