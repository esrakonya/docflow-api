package io.docflow.api.core.document.repository;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>{
    List<Document> findAllByClient(ApiClient client);

    Optional<Document> findByIdAndClient(UUID id, ApiClient client);
}
