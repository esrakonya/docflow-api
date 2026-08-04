package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByApiKeyHash(String apiKeyHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ApiClient c SET c.remainingQuota = c.remainingQuota - 1 WHERE c.id = :clientId AND c.remainingQuota > 0")
    int decrementRemainingQuota(@Param("clientId") UUID clientId);
}
