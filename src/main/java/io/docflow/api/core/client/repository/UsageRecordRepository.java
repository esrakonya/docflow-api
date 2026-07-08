package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UsageRecord> findByClientAndUsageMonth(ApiClient client, String usageMonth);
}
