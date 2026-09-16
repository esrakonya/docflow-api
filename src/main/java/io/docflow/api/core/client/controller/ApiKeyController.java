package io.docflow.api.core.client.controller;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.dto.ApiKeySummaryResponse;
import io.docflow.api.core.client.dto.CreateApiKeyRequest;
import io.docflow.api.core.client.dto.CreateApiKeyResponse;
import io.docflow.api.core.client.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> createKey(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        CreateApiKeyResponse response = apiKeyService.createKey(currentClient.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeySummaryResponse>> listKeys(
            @AuthenticationPrincipal ApiClientDto currentClient
    ) {
        return ResponseEntity.ok(apiKeyService.listKeys(currentClient.getId()));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> revokeKey(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @PathVariable UUID keyId
    ) {
        apiKeyService.revokeKey(currentClient.getId(), keyId);
        return ResponseEntity.noContent().build();
    }
}
