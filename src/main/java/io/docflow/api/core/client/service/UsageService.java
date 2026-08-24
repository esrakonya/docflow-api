package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import io.docflow.api.infrastructure.exception.QuotaExceededException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;

    @Transactional
    public int checkAndReturnRemaining(ApiClientDto clientDto) {
        return checkAndReturnRemaining(clientDto, 1);
    }

    @Transactional
    public int checkAndReturnRemaining(ApiClientDto clientDto, int amount) {
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        int monthlyQuota = clientDto.getMonthlyQuota();

        log.debug("Checking quota for client: {} (Month: {}, Requested: {}, Limit: {})", clientDto.getId(), currentMonth, amount, monthlyQuota);

        int newCount = usageRecordRepository
                .incrementIfUnderQuota(clientDto.getId(), currentMonth, amount, monthlyQuota)
                .orElseThrow(() -> {
                    log.warn("Quota exceeded for client: {}. Plan limit: {}", clientDto.getId(), monthlyQuota);
                    return new QuotaExceededException("Monthly quota exceeded for your current plan.");
                });

        return monthlyQuota - newCount;
    }
}
