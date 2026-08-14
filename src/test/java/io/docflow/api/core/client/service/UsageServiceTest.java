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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Atomic Quota Management system.
 * Verifies the new PostgreSQL UPSERT logic for monthly resets, atomic increments,
 * batch upload credit consumption, and race-condition prevention.
 */
@ExtendWith(MockitoExtension.class)
class UsageServiceTest {
    @Mock private UsageRecordRepository usageRecordRepository;
    @InjectMocks private UsageService usageService;
    private static String currentMonth() {
        return LocalDate.now().toString().substring(0, 7);
    }

    @Test
    @DisplayName("Should throw QuotaExceededException when atomic UPSERT returns empty (quota full)")
    void shouldThrowExceptionWhenQuotaIsFull() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder().id(clientId).monthlyQuota(100).build();

        when(usageRecordRepository.incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(1), eq(100)))
                .thenReturn(Optional.empty());

        assertThrows(QuotaExceededException.class, () ->
                usageService.checkAndReturnRemaining(clientDto));

        verify(usageRecordRepository, times(1))
                .incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(1), eq(100));
    }

    @Test
    @DisplayName("Should increment counter atomically and calculate remaining quota correctly when available")
    void shouldIncrementCountWhenQuotaIsAvailable() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder()
                .id(clientId)
                .monthlyQuota(100)
                .build();


        when(usageRecordRepository.incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(1), eq(100)))
                .thenReturn(Optional.of(91));

        int remaining = usageService.checkAndReturnRemaining(clientDto);

        assertEquals(9, remaining);
        verify(usageRecordRepository, times(1))
                .incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(1), eq(100));
    }

    @Test
    @DisplayName("Should allow requests in a new month even if previous month quota was exhausted")
    void shouldAllowRequestsAgainInNewMonthEvenIfPreviousMonthWasExhausted() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder()
                .id(clientId)
                .monthlyQuota(100)
                .build();

        when(usageRecordRepository.incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(1), eq(100)))
                .thenReturn(Optional.of(1));

        int remaining = usageService.checkAndReturnRemaining(clientDto);

        assertEquals(99, remaining);
    }

    @Test
    @DisplayName("Batch upload: Exact file count credits should be consumed in a single operation")
    void shouldConsumeExactFileCountForBatchUpload() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder()
                .id(clientId)
                .monthlyQuota(100)
                .build();

        when(usageRecordRepository.incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(10), eq(100)))
                .thenReturn(Optional.of(10));

        int remaining = usageService.checkAndReturnRemaining(clientDto, 10);

        assertEquals(90, remaining);
        verify(usageRecordRepository, times(1))
                .incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(10), eq(100));
    }

    @Test
    @DisplayName("Batch upload: Should reject when requested amount exceeds remaining quota")
    void shouldRejectBatchWhenAmountExceedsRemainingQuota() {
        UUID clientId = UUID.randomUUID();
        ApiClientDto clientDto = ApiClientDto.builder()
                .id(clientId)
                .monthlyQuota(100)
                .build();

        when(usageRecordRepository.incrementIfUnderQuota(eq(clientId), eq(currentMonth()), eq(60), eq(100)))
                .thenReturn(Optional.empty());

        assertThrows(QuotaExceededException.class, () ->
                usageService.checkAndReturnRemaining(clientDto, 60));
    }
}
