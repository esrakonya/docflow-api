package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.UsageRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UsageRecord> findByClientAndUsageMonth(ApiClient client, String usageMonth);

    @Query(value = """
            WITH upsert AS (
                INSERT INTO usage_records (id, client_id, usage_month, request_count)
                SELECT gen_random_uuid(), :clientId, :month, :amount
                WHERE :amount <= :quota
                ON CONFLICT (client_id, usage_month)
                DO UPDATE SET request_count = usage_records.request_count + :amount
                WHERE usage_records.request_count + :amount <= :quota
                RETURNING request_count
            )
            SELECT request_count FROM upsert
            """, nativeQuery = true)
    Optional<Integer> incrementIfUnderQuota(
            @Param("clientId") UUID clientId,
            @Param("month") String month,
            @Param("amount") int amount,
            @Param("quota") int quota
    );
}
