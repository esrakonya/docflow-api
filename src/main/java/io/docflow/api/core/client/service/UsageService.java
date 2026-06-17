package io.docflow.api.core.client.service;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import io.docflow.api.core.client.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;

    @Transactional
    public boolean checkAndIncrement(ApiClient client) {
        String currentMonth = LocalDate.now().toString().substring(0, 7);

        UsageRecord usageRecord =usageRecordRepository.findByClientAndUsageMonth(client, currentMonth)
                .orElse(UsageRecord.builder()
                        .client(client)
                        .usageMonth(currentMonth)
                        .request_count(0)
                        .build());

        if (usageRecord.getRequest_count() >= client.getMonthlyQuota()) {
            return false;
        }

        usageRecord.setRequest_count(usageRecord.getRequest_count() + 1);
        usageRecordRepository.save(usageRecord);
        return true;
    }
}
