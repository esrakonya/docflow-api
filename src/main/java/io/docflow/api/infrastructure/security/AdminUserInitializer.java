package io.docflow.api.infrastructure.security;

import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (adminUserRepository.findByUserName(adminUsername).isEmpty()) {
            log.info("No admin user found. Creating initial admin: {}", adminUsername);
            AdminUser admin = AdminUser.builder()
                    .userName(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("ROLE_ADMIN")
                    .build();
            adminUserRepository.save(admin);
        }
    }
}
