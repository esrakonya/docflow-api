package io.docflow.api.core.client.controller;

import io.docflow.api.core.client.dto.ClientRegistrationRequest;
import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/clients")
@RequiredArgsConstructor
public class ClientAdminController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ClientRegistrationResponse> register(@Valid @RequestBody ClientRegistrationRequest request) {
        ClientRegistrationResponse response = clientService.registerNewClient(request.name());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestParam ClientStatus status) {
        clientService.updateClientStatus(id, status);
        return ResponseEntity.noContent().build();
    }
}
