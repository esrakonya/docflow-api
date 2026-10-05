package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.infrastructure.exception.RateLimitExceededException;
import io.docflow.api.infrastructure.exception.RateLimitUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitingService service;
    private ApiClientDto client;

    @BeforeEach
    void setUp() {
        service = new RateLimitingService(redisTemplate);

        client = ApiClientDto.builder()
                .id(UUID.randomUUID())
                .companyName("Test Co")
                .status(ClientStatus.ACTIVE)
                .planName("FREE")
                .rateLimitPerMin(5)
                .build();
    }

    @Test
    void underLimit_doesNotThrow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(3L);

        assertThatCode(() -> service.checkRateLimit(client))
                .doesNotThrowAnyException();

        verify(redisTemplate, never()).expire(anyString(), any());
    }

    @Test
    void firstRequestInWindow_setsExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service.checkRateLimit(client);

        verify(redisTemplate)
                .expire(eq("ratelimit:" + client.getId()), any());
    }

    @Test
    void overLimit_throwsRateLimitExceeded_notUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(6L);

        assertThatThrownBy(() -> service.checkRateLimit(client))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void redisConnectionFailure_throwsRateLimitUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> service.checkRateLimit(client))
                .isInstanceOf(RateLimitUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void redisTimeoutOnExpire_throwsRateLimitUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any()))
                .thenThrow(new QueryTimeoutException("timed out"));

        assertThatThrownBy(() -> service.checkRateLimit(client))
                .isInstanceOf(RateLimitUnavailableException.class)
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }
}
