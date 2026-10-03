package io.docflow.api.core.admin.service;

import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Owns the "does an admin user exist, and if not, create one" decision.
 * <p>+ * Deliberately framework-thin: no {@code @Value}, no {@link org.springframework.boot.CommandLineRunner}.
 * Callers (e.g. a startup runner) pass in the desired username/password; this class only knows
 * how to look up, hash and persist. That split is what makes it testable with plain Mockito,
 * without a Spring context.
 */
@Service
@RequiredArgsConstructor
public class AdminSeedService {

    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public enum SeedResult {
        CREATED,
        ALREADY_EXISTS
    }

    /**
     * Ensures an admin user with {@code username} exists, creating one with the given
     * (BCrypt-hashed) password if it doesn't.
     * @return CREATED if a new admin was persisted, ALREADY_EXISTS if one was already present
     * (in which case the given password is ignored, matching prior behavior).
     * */
    public SeedResult seeAdmin(String username, String rawPassword) {
        if (adminUserRepository.findByUserName(username).isPresent()) {
            return SeedResult.ALREADY_EXISTS;
        }

        AdminUser admin = AdminUser.builder()
                .userName(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(ADMIN_ROLE)
                .createdAt(LocalDateTime.now())
                .build();
        adminUserRepository.save(admin);
        return SeedResult.CREATED;
    }
}
