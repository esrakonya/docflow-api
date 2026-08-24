package io.docflow.api.core.client.service;

import io.docflow.api.core.billing.entity.Plan;
import io.docflow.api.core.billing.entity.PlanTier;
import io.docflow.api.core.billing.repository.PlanRepository;
import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.core.document.mapper.DocumentMapper;
import io.docflow.api.infrastructure.exception.PaymentProcessingException;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ApiClientRepository apiClientRepository;
    private final PlanRepository planRepository;
    private final DocumentMapper documentMapper;
    private final ClientCacheService clientCacheService;

    @Transactional
    public ClientRegistrationResponse registerNewClient(String companyName) {
        log.info("Registering new client for company: {}", companyName);

        Plan freePlan = planRepository.findByName(PlanTier.FREE)
                .orElseThrow(() -> {
                    log.error("CRITICAL: Default FREE plan not found in database!");
                    return new PaymentProcessingException("System configuration error: Default subscription tier missing.");
                });

        String rawKey = "invox_live_" + UUID.randomUUID().toString().replace("-", "");
        String hashedkey = HashUtils.sha256(rawKey);

        String webhookSecret = UUID.randomUUID().toString().replace("-", "");

        ApiClient client = ApiClient.builder()
                .companyName(companyName)
                .apiKeyHash(hashedkey)
                .webhookSecret(webhookSecret)
                .plan(freePlan)
                .status(ClientStatus.ACTIVE)
                .build();

        ApiClient saved = apiClientRepository.save(client);

        log.info("Client registered successfully with Plan: {}", PlanTier.FREE);

        return documentMapper.toRegistrationResponse(saved, rawKey);
    }

    @Transactional
    public void updateClientStatus(UUID clientId, ClientStatus newStatus) {
        ApiClient client = apiClientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        client.setStatus(newStatus);
        apiClientRepository.save(client);

        clientCacheService.evictCacheByHash(client.getApiKeyHash());

        log.info("Client {} status uploaded to {} and cache cleared.", clientId, newStatus);
    }
}
