package io.docflow.api.core.client.repository;

import io.docflow.api.core.client.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByClientIdAndActiveTrue(UUID clientId);

    List<ApiKey> findAllByClientIdOrderByCreatedAtDesc(UUID clientId);

    Optional<ApiKey> findByIdAndClientId(UUID id, UUID clientId);

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.client c JOIN FETCH c.plan " +
            "WHERE k.keyHash = :hash AND k.active = true")
    Optional<ApiKey> findActiveKeyWithClientAndPlan(@Param("hash") String hash);
}
