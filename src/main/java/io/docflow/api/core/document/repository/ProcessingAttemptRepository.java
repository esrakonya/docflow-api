package io.docflow.api.core.document.repository;

import io.docflow.api.core.document.entity.ProcessingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttempt, UUID> {
}
