package io.docflow.api.infrastructure.security;

import io.docflow.api.core.client.repository.ApiClientRepository;
import io.docflow.api.infrastructure.util.HashUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiClientRepository apiClientRepository;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();

        if (!path.startsWith("/api/v1/documents")) {
            String apiKey = request.getHeader("X-API-KEY");
            if (apiKey != null) {
                String hashedKey = HashUtils.sha256(apiKey);
                apiClientRepository.findByApiKeyHash(hashedKey).ifPresent(client -> {
                    var auth = new UsernamePasswordAuthenticationToken(client, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}
