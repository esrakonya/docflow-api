package io.docflow.api.core.extraction.repository;

import io.docflow.api.core.extraction.entity.ExtractedData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExtractedDataRepository extends JpaRepository<ExtractedData, UUID> {
    Optional<ExtractedData> findByDocumentId(UUID documentId);
}
