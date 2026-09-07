package io.docflow.api.core.client.controller;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.dto.ClientUsageResponse;
import io.docflow.api.core.client.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UsageService usageService;

    @GetMapping("/usage")
    public ResponseEntity<ClientUsageResponse> getMyUsage(
            @AuthenticationPrincipal ApiClientDto currentClient) {

        return ResponseEntity.ok(usageService.getCurrentUsage(currentClient));
    }
}
