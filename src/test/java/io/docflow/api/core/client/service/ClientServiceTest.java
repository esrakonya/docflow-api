package io.docflow.api.core.client.service;

import io.docflow.api.core.client.entity.ApiClient;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.repository.ApiClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ClientService.
 * Focuses on client lifecycle management, status updates, and ensuring
 * cache consistency (eviction) when client metadata changes.
 */
@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {
    @Mock private ApiClientRepository apiClientRepository;
    @Mock private ClientCacheService clientCacheService;
    @InjectMocks private ClientService clientService;

    @Test
    @DisplayName("Should evict cache immediately when client status changes")
    void shouldEvictCacheWhenStatusChanges() {
        UUID clientId = UUID.randomUUID();
        ApiClient client = ApiClient.builder().id(clientId).apiKeyHash("hash123").build();
        when(apiClientRepository.findById(clientId)).thenReturn(Optional.of(client));

        clientService.updateClientStatus(clientId, ClientStatus.SUSPENDED);

        verify(apiClientRepository).save(client);
        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
        verify(clientCacheService).evictCacheByHash("hash123");
    }
}
