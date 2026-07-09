package io.docflow.api.core.client.controller;

import io.docflow.api.core.client.dto.ClientRegistrationRequest;
import io.docflow.api.core.client.dto.ClientRegistrationResponse;
import io.docflow.api.core.client.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/clients")
@RequiredArgsConstructor
public class ClientAdminController {

    private final ClientService clientService;

    @PostMapping
    public ClientRegistrationResponse register(@Valid @RequestBody ClientRegistrationRequest request) {
        return clientService.registerNewClient(request.name());
    }
}
