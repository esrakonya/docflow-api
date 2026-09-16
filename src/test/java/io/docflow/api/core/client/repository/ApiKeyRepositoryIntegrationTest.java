package io.docflow.api.core.client.repository;

import io.docflow.api.BaseIntegrationTest;
import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ApiKey;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.infrastructure.util.HashUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the real SQL/JPQL query on the authentication hot path
 * against a real Postgres instance — this is the query that decides
 * whether a raw API key is accepted or rejected on every request.
 */
public class ApiKeyRepositoryIntegrationTest extends BaseIntegrationTest {
    @Autowired private ApiKeyRepository apiKeyRepository;

    @Test
    @DisplayName("Finds an active key together with its client and plan in one query")
    void shouldFindActiveKeyWithClientAndPlan() {
        Plan freePlan = getFreePlan();
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Repo Test Co")
                .status(ClientStatus.ACTIVE)
                .plan(freePlan)
                .build());

        String hash = HashUtils.sha256("some-raw-key");
        apiKeyRepository.save(ApiKey.builder()
                .client(client)
                .keyHash(hash)
                .label("Test Key")
                .active(true)
                .build());

        Optional<ApiKey> found = apiKeyRepository.findActiveKeyWithClientAndPlan(hash);

        assertTrue(found.isPresent());
        assertEquals(client.getId(), found.get().getClient().getId());
        assertEquals(freePlan.getName(), found.get().getClient().getPlan().getName());
    }

    @Test
    @DisplayName("Does not return a key that has been deactivated")
    void shouldNotFindInactiveKey() {
        Plan freePlan = getFreePlan();
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Inactive Key Co")
                .status(ClientStatus.ACTIVE)
                .plan(freePlan)
                .build());

        String hash = HashUtils.sha256("deactivated-key");
        apiKeyRepository.save(ApiKey.builder()
                .client(client)
                .keyHash(hash)
                .label("Revoked Key")
                .active(false)
                .build());

        assertTrue(apiKeyRepository.findActiveKeyWithClientAndPlan(hash).isEmpty());
    }

    @Test
    @DisplayName("A client can have two independent, active keys at the same time")
    void shouldSupportMultipleKeysPerClient() {
        Plan freePlan = getFreePlan();
        ApiClient client = apiClientRepository.save(ApiClient.builder()
                .companyName("Multi Key Co")
                .status(ClientStatus.ACTIVE)
                .plan(freePlan)
                .build());

        String hashA = HashUtils.sha256("key-a");
        String hashB = HashUtils.sha256("key-b");

        apiKeyRepository.save(ApiKey.builder().client(client).keyHash(hashA).label("Prod").active(true).build());
        apiKeyRepository.save(ApiKey.builder().client(client).keyHash(hashB).label("Test").active(true).build());

        assertTrue(apiKeyRepository.findActiveKeyWithClientAndPlan(hashA).isPresent());
        assertTrue(apiKeyRepository.findActiveKeyWithClientAndPlan(hashB).isPresent());
        assertEquals(2, apiKeyRepository.findAllByClientIdAndActiveTrue(client.getId()).size());
    }
}
