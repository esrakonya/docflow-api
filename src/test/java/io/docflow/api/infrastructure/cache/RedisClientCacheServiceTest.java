package io.docflow.api.infrastructure.cache;

import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ApiKey;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.repository.ApiKeyRepository;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisClientCacheServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private RedisClientCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RedisClientCacheService(redisTemplate, apiKeyRepository);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Cache hit: returns cached DTO without touching the database")
    void shouldReturnFromCacheWithoutHittingDb() {
        String rawKey = "invox_live_test123";
        String hash = HashUtils.sha256(rawKey);
        ApiClientDto cached = ApiClientDto.builder().id(UUID.randomUUID()).build();

        when(valueOperations.get("api_key" + hash)).thenReturn(cached);

        Optional<ApiClientDto> result = cacheService.getClientByApiKey(rawKey);

        assertTrue(result.isPresent());
        assertEquals(cached, result.get());
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    @DisplayName("Cache miss + valid active key: loads from DB, builds DTO, writes back to cache")
    void shouldLoadFromDbAndCacheOnMiss() {
        String rawKey = "invox_live_test456";
        String hash = HashUtils.sha256(rawKey);

        when(valueOperations.get("api_key" + hash)).thenReturn(null);

        Plan plan = Plan.builder().name("FREE").monthlyQuota(100).rateLimitPerMin(10).build();
        ApiClient client = ApiClient.builder()
                .id(UUID.randomUUID())
                .companyName("Test Co")
                .status(ClientStatus.ACTIVE)
                .plan(plan)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .keyHash(hash)
                .client(client)
                .active(true)
                .build();

        when(apiKeyRepository.findActiveKeyWithClientAndPlan(hash)).thenReturn(Optional.of(apiKey));

        Optional<ApiClientDto> result = cacheService.getClientByApiKey(rawKey);

        assertTrue(result.isPresent());
        assertEquals(client.getId(), result.get().getId());
        assertEquals("FREE", result.get().getPlanName());
        assertEquals(100, result.get().getMonthlyQuota());
        verify(valueOperations).set(eq("api_key" + hash), any(ApiClientDto.class), any(Duration.class));

    }

    @Test
    @DisplayName("Expired key: rejected even though it exists and is marked active")
    void shouldRejectExpiredKey() {
        String rawKey = "invox_live_expired";
        String hash = HashUtils.sha256(rawKey);

        when(valueOperations.get("api_key" + hash)).thenReturn(null);

        Plan plan = Plan.builder().name("FREE").build();
        ApiClient client = ApiClient.builder().id(UUID.randomUUID()).plan(plan).build();
        ApiKey expiredKey = ApiKey.builder()
                .keyHash(hash)
                .client(client)
                .active(true)
                .expiresAt(OffsetDateTime.now().minusDays(1))
                .build();

        when(apiKeyRepository.findActiveKeyWithClientAndPlan(hash)).thenReturn(Optional.of(expiredKey));

        Optional<ApiClientDto> result = cacheService.getClientByApiKey(rawKey);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Unknown key: returns empty")
    void shouldReturnEmptyForUnknownKey() {
        String rawKey = "invox_live_unknown";
        String hash = HashUtils.sha256(rawKey);

        when(valueOperations.get("api_key" + hash)).thenReturn(null);
        when(apiKeyRepository.findActiveKeyWithClientAndPlan(hash)).thenReturn(Optional.empty());

        Optional<ApiClientDto> result = cacheService.getClientByApiKey(rawKey);

        assertTrue(result.isEmpty());
    }
}
