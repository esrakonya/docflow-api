package io.docflow.api.core.client.service;

import io.docflow.api.config.AppProperties;
import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.infrastructure.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    public void checkRateLimit(ApiClientDto clientDto) {
        String key = "ratelimit:" + clientDto.getId();
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        int limit = clientDto.getRateLimitPerMin();

        if (currentCount != null && currentCount > limit) {
            log.warn("Rate limit exceeded for client: {} (Plan: {}, Limit: {})", clientDto.getId(), clientDto.getPlanName(), limit);
            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded for %s tier (%d requests per minute)", clientDto.getPlanName(), limit)
            );
        }
    }
}
