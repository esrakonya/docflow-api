package io.docflow.api.core.client.service;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import io.docflow.api.infrastructure.exception.QuotaExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {
    @Mock private UsageRecordRepository usageRecordRepository;
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
    }

    @Test
    @DisplayName("Kota müsaitse sayacı artırmalı ve kalan sayıyı dönmeli")
    void shouldIncrementCountWhenQuotaIsAvailable() {
        ApiClient client = ApiClient.builder().monthlyQuota(10).build();
        UsageRecord record = UsageRecord.builder().requestCount(2).build();

        when(usageRecordRepository.findByClientAndUsageMonth(any(), any()))
                .thenReturn(Optional.of(record));

        int remaining = usageService.checkAndReturnRemaining(client);

        assertEquals(7, remaining);
        verify(usageRecordRepository, times(1)).save(record);
    }
}
