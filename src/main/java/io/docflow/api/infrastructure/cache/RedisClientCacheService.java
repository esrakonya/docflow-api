package io.docflow.api.infrastructure.cache;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ApiKey;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.ApiKeyRepository;
import io.docflow.api.core.client.service.ClientCacheService;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisClientCacheService implements ClientCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiKeyRepository apiKeyRepository;

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

        Optional<ApiKey> validKey = apiKeyRepository.findActiveKeyWithClientAndPlan(hashedKey)
                .filter(key -> {
                    if (key.isExpired()) {
                        log.warn("Rejected expired API key (label: {}, client: {})", key.getLabel(), key.getClient().getId());
                        return false;
                    }
                    return true;
                });

        return validKey.map(key -> {
            ApiClientDto dto = mapToDto(key);

            try {
                redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Failed to write to Redis: {}", e.getMessage());
            }

            log.debug("Cache miss. Client loaded from DB and cached: {}", hashedKey);
            return dto;
        });
    }

    @Override
    public void evictClientCache(String apiKey) {
        redisTemplate.delete(CACHE_KEY_PREFIX + HashUtils.sha256(apiKey));
    }

    @Override
    public void evictCacheByHash(String apiKeyHash) {
        String cacheKey = CACHE_KEY_PREFIX + apiKeyHash;
        redisTemplate.delete(cacheKey);
        log.info("Cache evicted for API Key Hash: {}", apiKeyHash);
    }

    @Override
    public void evictAllCacheForClient(UUID clientId) {
        List<ApiKey> keys = apiKeyRepository.findAllByClientIdAndActiveTrue(clientId);

        for (ApiKey key : keys) {
            evictCacheByHash(key.getKeyHash());
        }

        log.info("Evicted cache for all {} active key(s) of client {}", keys.size(), clientId);
    }

    private ApiClientDto mapToDto(ApiKey apiKey) {
        ApiClient entity = apiKey.getClient();
        return ApiClientDto.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .apiKeyHash(apiKey.getKeyHash())
                .status(entity.getStatus())
                .monthlyQuota(entity.getPlan().getMonthlyQuota())
                .rateLimitPerMin(entity.getPlan().getRateLimitPerMin())
                .planName(entity.getPlan().getName())
                .expiresAt(apiKey.getExpiresAt())
                .build();
    }
}
