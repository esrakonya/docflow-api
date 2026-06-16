package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);
}
