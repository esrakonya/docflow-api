package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import io.docflow.api.infrastructure.exception.QuotaExceededException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;
    private final ApiClientRepository apiClientRepository;

    @Transactional
    public int checkAndReturnRemaining(ApiClientDto clientDto) {
        int updatedRows = apiClientRepository.decrementRemainingQuota(clientDto.getId());

        if (updatedRows == 0) {
            throw new QuotaExceededException("Insufficient quota! All limits have been exceeded, including concurrent requests.");
        }

        ApiClient clientEntity = apiClientRepository.getReferenceById(clientDto.getId());

        String currentMonth = LocalDate.now().toString().substring(0, 7);

        UsageRecord usageRecord =usageRecordRepository.findByClientAndUsageMonth(clientEntity, currentMonth)
                .orElse(UsageRecord.builder()
                        .client(clientEntity)
                        .usageMonth(currentMonth)
                        .requestCount(0)
                        .build());

        if (usageRecord.getRequestCount() >= clientDto.getMonthlyQuota()) {
            throw new QuotaExceededException("Quota exceeded for this month");
        }

        usageRecord.setRequestCount(usageRecord.getRequestCount() + 1);
        usageRecordRepository.save(usageRecord);

        clientDto.setRemainingQuota(clientDto.getRemainingQuota() - 1);

        return clientDto.getRemainingQuota();
    }
}
