package io.docflow.api.web.dashboard;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.service.ClientCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    private final ClientCacheService clientCacheService;

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

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody DashboardLoginRequest request, HttpServletRequest httpRequest) {
        Optional<ApiClientDto> clientDtoOpt = clientCacheService.getClientByApiKey(request.apiKey())
                .filter(dto -> dto.getStatus() == ClientStatus.ACTIVE);

        if (clientDtoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ApiClientDto clientDto = clientDtoOpt.get();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                clientDto, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENT")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return ResponseEntity.ok(Map.of("message", "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
