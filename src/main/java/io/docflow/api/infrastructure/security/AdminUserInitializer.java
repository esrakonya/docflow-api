package io.docflow.api.infrastructure.security;

import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import io.docflow.api.core.admin.service.AdminSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Startup adapter only: reads admin credentials from config and delegates the actual
 * find-or-create decision to {@link AdminSeedService}, which holds the testable logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String DEFAULT_PASSWORD = "admin123";

    private final AdminSeedService adminSeedService;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("########################################################");
            log.warn("SECURITY WARNING: DEFAULT ADMIN PASSWORD IS IN USE!");
            log.warn("Please change 'app.admin.password' in production env.");
            log.warn("########################################################");
        }

        AdminSeedService.SeedResult result = adminSeedService.seeAdmin(adminUsername, adminPassword);
        if (result == AdminSeedService.SeedResult.CREATED) {
            log.info("No admin user found. Created initial admin: {}", adminUsername);
        }
    }
}
