package io.docflow.api.core.extraction.repository;

import io.docflow.api.core.extraction.entity.ExtractedData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExtractedDataRepository extends JpaRepository<ExtractedData, UUID> {
    Optional<ExtractedData> findByDocumentId(UUID documentId);

    @Query("SELECT e FROM ExtractedData e LEFT JOIN FETCH e.lineItems WHERE e.document.id = :documentId")
    Optional<ExtractedData> findByDocumentIdWithItems(@Param("documentId") UUID documentId);
}
