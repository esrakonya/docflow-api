package io.docflow.api.core.common.repository;

import io.docflow.api.core.common.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByProcessedFalseOrderByCreatedAtAsc();
}
