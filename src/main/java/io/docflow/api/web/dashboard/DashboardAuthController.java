package io.docflow.api.web.dashboard;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.service.ClientCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardAuthController {

    private final DashboardSessionService dashboardSessionService;

    /**
     * Returns the current CSRF token so a non-form client (curl, a future
     * fetch()-based page interaction) can read it before making a state
     * changing request. Once real Thymeleaf forms exist, the token is
     * embedded automatically and this endpoint becomes optional, but it
     * stays useful for testing and for any JS-driven interaction later.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName()
        ));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody DashboardLoginRequest request, HttpServletRequest httpRequest) {
        Optional<ApiClientDto> clientDto = dashboardSessionService.login(request.apiKey(), httpRequest);

        if (clientDto.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(Map.of("message", "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        dashboardSessionService.logout(httpRequest);
        return ResponseEntity.noContent().build();
    }
}
