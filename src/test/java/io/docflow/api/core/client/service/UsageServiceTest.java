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

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {
    @Mock private UsageRecordRepository usageRecordRepository;
    @InjectMocks private UsageService usageService;
    private static String currentMonth() {
        return LocalDate.now().toString().substring(0, 7);
    }

    @Test
    @DisplayName("Atomik UPSERT boş dönerse (kota dolu) QuotaExceededException fırlatmalı")
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
    @DisplayName("Kota müsaitse sayaç atomik olarak artırılmalı ve kalan miktar doğru hesaplanmalı")
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
    @DisplayName("Yeni ay geldiğinde, önceki ay kotası tükenmiş olsa bile istek kabul edilmeli")
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
    @DisplayName("Batch yükleme: dosya sayısı kadar kredi TEK seferde düşmeli (bypass yok)")
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
    @DisplayName("Batch yükleme: talep edilen miktar kotayı aşıyorsa (kısmi kabul yok) reddedilmeli")
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
