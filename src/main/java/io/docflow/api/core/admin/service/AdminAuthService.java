package io.docflow.api.core.admin.service;

import io.docflow.api.core.admin.dto.AdminLoginRequest;
import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import io.docflow.api.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String authenticate(AdminLoginRequest request) {
        AdminUser admin = adminUserRepository.findByUserName(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            log.warn("Failed login attempt for admin user: {}", request.username());
            throw new BadCredentialsException("Invalid username or password");
        }

        log.info("Admin user authenticated successfully: {}", request.username());

        return jwtService.generateToken(admin.getUserName());
    }
}
