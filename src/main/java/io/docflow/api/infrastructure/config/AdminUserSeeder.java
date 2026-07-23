package io.docflow.api.infrastructure.config;

import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminUserSeeder implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (adminUserRepository.count() == 0) {
            log.info("No admin user found. Creating default admin user: {}", adminUsername);

            AdminUser defaultAdmin = AdminUser.builder()
                    .userName(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("ROLE_ADMIN")
                    .createdAt(LocalDateTime.now())
                    .build();

            adminUserRepository.save(defaultAdmin);
            log.info("Default admin user created successfully.");
        } else {
            log.debug("Admin user already exists. Skipping seeding.");
        }
    }
}
