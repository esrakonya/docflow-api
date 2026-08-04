package io.docflow.api.core.client.service;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import io.docflow.api.infrastructure.exception.QuotaExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {
    @Mock private UsageRecordRepository usageRecordRepository;
    @Mock private ApiClientRepository apiClientRepository;
    @InjectMocks private UsageService usageService;

    @Test
    @DisplayName("Kota dolduğunda QuotaExceededException fırlatmalı")
    void shouldThrowExceptionWhenQuotaIsFull() {
        ApiClient client = ApiClient.builder().monthlyQuota(5).build();
        UsageRecord fullRecord = UsageRecord.builder().requestCount(5).build();

        when(usageRecordRepository.findByClientAndUsageMonth(any(), any()))
                .thenReturn(Optional.of(fullRecord));

        assertThrows(QuotaExceededException.class, () ->
                usageService.checkAndReturnRemaining(client));

        verify(usageRecordRepository, never()).save(any());
        verify(apiClientRepository, never()).decrementRemainingQuota(any());
    }

    @Test
    @DisplayName("Kota müsaitse hem aylık kaydı hem de ana kotayı güncellemeli")
    void shouldIncrementCountWhenQuotaIsAvailable() {
        UUID clientId = UUID.randomUUID();
        ApiClient client = ApiClient.builder()
                .id(clientId)
                .monthlyQuota(10)
                .remainingQuota(8)
                .build();

        UsageRecord record = UsageRecord.builder().requestCount(2).build();

        when(usageRecordRepository.findByClientAndUsageMonth(any(), any()))
                .thenReturn(Optional.of(record));

        int remaining = usageService.checkAndReturnRemaining(client);

        assertEquals(7, remaining);
        verify(usageRecordRepository, times(1)).save(record);
        verify(apiClientRepository, times(1)).decrementRemainingQuota(clientId);
    }
}
