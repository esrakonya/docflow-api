package io.docflow.api.core.client.service;

import io.docflow.api.config.AppProperties;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.infrastructure.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public void checkRateLimit(ApiClient client) {
        String key = "ratelimit:" + client.getApiKeyHash();
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        int limit = "pro".equalsIgnoreCase(client.getPlanTier())
                ? appProperties.getSecurity().getProTierLimit()
                : appProperties.getSecurity().getFreeTierLimit();

        if (currentCount != null && currentCount > limit) {
            throw new RateLimitExceededException(
                    String.format("%s planı için dakikalık (%d) dolmuştur.", client.getPlanTier(), limit)
            );
        }
    }
}
