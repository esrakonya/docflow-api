package io.docflow.api.infrastructure.security;

import io.docflow.api.core.client.dto.ApiClientDto;
import io.docflow.api.core.client.entity.ClientStatus;
import io.docflow.api.core.client.service.ClientCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final  long EXPIRY_WARNING_THRESHOLD_DAYS = 7;

    private final ClientCacheService clientCacheService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            try {

                clientCacheService.getClientByApiKey(apiKey).ifPresent(clientDto -> {
                    if (clientDto.getStatus() != ClientStatus.ACTIVE) {
                        log.warn("Access denied for client '{}' with status: {}", clientDto.getCompanyName(), clientDto.getStatus());
                        throw new BadCredentialsException("API Key is " + clientDto.getStatus().name());
                    }

                    addExpiryWarningHeaderIfNeeded(response, clientDto);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            clientDto,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENT"))
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Client authenticated successfully via API Key: {}", clientDto.getCompanyName());
                });

            } catch (BadCredentialsException ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"" + ex.getMessage() + "\"}");
                return;
            } catch (Exception ex) {
                log.error("Authentication filter error: ", ex);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void addExpiryWarningHeaderIfNeeded(HttpServletResponse response, ApiClientDto clientDto) {
        OffsetDateTime expiresAt = clientDto.getExpiresAt();
        if (expiresAt == null) {
            return;
        }

        long daysUntilExpiry = Duration.between(OffsetDateTime.now(), expiresAt).toDays();
        if (daysUntilExpiry <= EXPIRY_WARNING_THRESHOLD_DAYS) {
            response.setHeader("X-API-Key-Expires-Soon", "true");
            response.setHeader("X-API-Key-Expires-At", expiresAt.toString());
        }
    }
}
