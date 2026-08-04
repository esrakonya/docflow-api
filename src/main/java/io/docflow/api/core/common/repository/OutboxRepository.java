package io.docflow.api.core.common.repository;

import io.docflow.api.core.common.entity.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

    @Query(value = "SELECT * FROM outbox_messages WHERE processed = false ORDER BY created_at ASC LIMIT 100", nativeQuery = true)
    List<OutboxMessage> findTop100PendingMessages();

    @Modifying
    @Query("DELETE FROM OutboxMessage o WHERE o.processed = true AND o.createdAt < :time")
    void purgeOldMessages(@Param("time") LocalDateTime time);
}
