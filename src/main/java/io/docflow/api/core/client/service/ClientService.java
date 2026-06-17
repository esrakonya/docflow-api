package io.docflow.api.core.client.service;

import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.infrastructure.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ApiClientRepository apiClientRepository;

    public ClientRegistrationResponse registerNewClient(String companyName) {
        String rawKey = "invox_live_" + UUID.randomUUID().toString().replace("-", "");

        String hashedkey = HashUtils.sha256(rawKey);

        ApiClient client = ApiClient.builder()
                .companyName(companyName)
                .apiKeyHash(hashedkey)
                .planTier("free")
                .monthlyQuota(100)
                .createdAt(OffsetDateTime.now())
                .build();

        ApiClient saved = apiClientRepository.save(client);

        return new ClientRegistrationResponse(saved.getId(), saved.getCompanyName(), rawKey);
    }
}
