package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    Optional<UsageRecord> findByClientAndUsageMonth(ApiClient client, String usageMonth);
}
