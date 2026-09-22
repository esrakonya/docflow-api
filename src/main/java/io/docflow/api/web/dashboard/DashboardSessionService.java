package io.docflow.api.web.dashboard;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.service.ClientCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardSessionService {

    private final ClientCacheService clientCacheService;

    /**
     * Validates a raw API key and, if valid and active, establishes an
     * authenticated browser session for it.
     */
    public Optional<ApiClientDto> login(String rawApiKey, HttpServletRequest httpRequest) {
        Optional<ApiClientDto> clientDtoOpt = clientCacheService.getClientByApiKey(rawApiKey)
                .filter(dto -> dto.getStatus() == ClientStatus.ACTIVE);

        if (clientDtoOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiClientDto clientDto = clientDtoOpt.get();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                clientDto, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENT")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return Optional.of(clientDto);
    }

    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
