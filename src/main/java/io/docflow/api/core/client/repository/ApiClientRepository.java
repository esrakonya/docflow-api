package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);

    @Query("SELECT c FROM ApiClient c JOIN FETCH c.plan WHERE c.apiKeyHash = :apiKeyHash")
    Optional<ApiClient> findByApiKeyHashWithPlan(@Param("apiKeyHash") String apiKeyHash);
}
