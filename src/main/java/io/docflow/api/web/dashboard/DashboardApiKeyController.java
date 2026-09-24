package io.docflow.api.web.dashboard;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.dto.CreateApiKeyRequest;
import io.docflow.api.core.client.dto.CreateApiKeyResponse;
import io.docflow.api.core.client.service.ApiKeyService;
import io.docflow.api.infrastructure.exception.LastActiveApiKeyException;
import io.docflow.api.infrastructure.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
  * Session-authenticated (browser) equivalent of {@link io.docflow.api.core.client.controller.ApiKeyController}.
  * <p>
  * This intentionally does NOT call the REST API under {@code /api/v1/me/keys} — that
  * endpoint lives on a stateless filter chain that only accepts {@code X-API-KEY} header
  * auth (see {@code SecurityConfig#apiFilterChain}). The dashboard runs on the separate
  * session-based {@code /dashboard/**} filter chain, so it talks to {@link ApiKeyService}
  * directly instead.
  */

@Controller
@RequestMapping("/dashboard/keys")
@RequiredArgsConstructor
@Slf4j
public class DashboardApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public String showKeysPage(@AuthenticationPrincipal ApiClientDto currentClient, Model model) {
        model.addAttribute("createRequest", new CreateApiKeyRequest(""));
        model.addAttribute("keys", apiKeyService.listKeys(currentClient.getId()));
        model.addAttribute("companyName", currentClient.getCompanyName());

        return "dashboard/keys";
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String createKey(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @Valid @ModelAttribute("createRequest") CreateApiKeyRequest createRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("keys", apiKeyService.listKeys(currentClient.getId()));
            model.addAttribute("companyName", currentClient.getCompanyName());

            return "dashboard/keys";
        }

        CreateApiKeyResponse response = apiKeyService.createKey(currentClient.getId(), createRequest);

        redirectAttributes.addFlashAttribute("newKey", response);
        return "redirect:/dashboard/keys";
    }

    @PostMapping(value = "/{keyId}/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String revokeKey(
            @AuthenticationPrincipal ApiClientDto currentClient,
            @PathVariable UUID keyId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            apiKeyService.revokeKey(currentClient.getId(), keyId);
            redirectAttributes.addFlashAttribute("success", "API key revoked.");
        } catch (LastActiveApiKeyException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (ResourceNotFoundException e) {
            log.warn("Client {} attempted to revoke unknown/foreign key {}", currentClient.getId(), keyId);
            redirectAttributes.addFlashAttribute("error", "API key not found.");
        }

        return "redirect:/dashboard/keys";
    }
}
