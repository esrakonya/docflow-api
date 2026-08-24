package io.docflow.api.core.billing.repository;

import io.docflow.api.core.billing.entity.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, UUID> {
    boolean existsByEventId(String eventId);
}
