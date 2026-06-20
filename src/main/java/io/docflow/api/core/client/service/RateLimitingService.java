package io.docflow.api.core.client.service;

import io.docflow.api.infrastructure.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    public void checkRateLimit(String apiKeyHash) {
        String key = "rate_limit:" + apiKeyHash;

        Long currentRequestCount = redisTemplate.opsForValue().increment(key);

        if (currentRequestCount != null && currentRequestCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        if (currentRequestCount != null && currentRequestCount > 5) {
            throw new RateLimitExceededException("Dakikalık istek limitinizi aştınız. Lütfen bekleyin.");
        }
    }
}
