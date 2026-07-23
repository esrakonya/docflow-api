package io.docflow.api.core.admin.service;

import io.docflow.api.core.admin.dto.AdminLoginRequest;
import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public void authenticate(AdminLoginRequest request) {
        AdminUser admin = adminUserRepository.findByUserName(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // Buradan sonra mülakatta "Burada JWT Token üretiyorum" diyebilirsin.
        // Şimdilik sadece yetkiyi onaylıyoruz.
    }
}
