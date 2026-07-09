package io.docflow.api.core.document.repository;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.document.entity.Document;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>{
    Page<Document> findAllByClient(ApiClient client, Pageable pageable);

    Optional<Document> findByIdAndClient(UUID id, ApiClient client);

    @Query("SELECT d FROM Document d JOIN FETCH d.client WHERE d.id = :id")
    Optional<Document> findByIdWithClient(@Param("id") UUID id);
}
