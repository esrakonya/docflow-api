package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;
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
    @DisplayName("Atomik kota düşümü 0 dönerse QuotaExceededException fırlatmalı")
    void shouldThrowExceptionWhenQuotaIsFull() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).build();

        when(apiClientRepository.decrementRemainingQuota(clientId)).thenReturn(0);

        assertThrows(QuotaExceededException.class, () ->
                usageService.checkAndReturnRemaining(clientDto));

        verify(apiClientRepository, times(1)).decrementRemainingQuota(any());
        verify(usageRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kota müsaitse hem aylık kaydı hem de ana kotayı güncellemeli")
    void shouldIncrementCountWhenQuotaIsAvailable() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder()
                .id(clientId)
                .monthlyQuota(100)
                .remainingQuota(10)
                .build();

        UsageRecord record = UsageRecord.builder().requestCount(5).build();

        when(apiClientRepository.decrementRemainingQuota(clientId)).thenReturn(1);

        when(usageRecordRepository.findByClientAndUsageMonth(any(), any()))
                .thenReturn(Optional.of(record));

        int remaining = usageService.checkAndReturnRemaining(clientDto);

        assertEquals(9, remaining);
        verify(apiClientRepository, times(1)).decrementRemainingQuota(clientId);
        verify(usageRecordRepository, times(1)).save(record);
    }
}
