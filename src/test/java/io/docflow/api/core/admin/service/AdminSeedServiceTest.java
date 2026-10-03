package io.docflow.api.core.admin.service;

import io.docflow.api.core.admin.entity.AdminUser;
import io.docflow.api.core.admin.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit test: no Spring context, no DB, no Testcontainers.
 * This is exactly the kind of test that was impossible while this logic lived
 * inside a CommandLineRunner with field-injected @Value properties.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeedServiceTest {
    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeedService service() {
        return new AdminSeedService(adminUserRepository, passwordEncoder);
    }

    @Test
    void createsAdmin_whenUsernameNotFound() {
        when(adminUserRepository.findByUserName("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");

        AdminSeedService.SeedResult result = service().seeAdmin("admin", "secret123");

        assertThat(result).isEqualTo(AdminSeedService.SeedResult.CREATED);

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);

        verify(adminUserRepository).save(captor.capture());

        AdminUser saved = captor.getValue();
        assertThat(saved.getUserName()).isEqualTo("admin");
        assertThat(saved.getPassword()).isEqualTo("hashed-secret");
        assertThat(saved.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void doesNotCreateAdmin_whenUsernameAlreadyExists() {
        AdminUser existing = AdminUser.builder().userName("admin").build();

        when(adminUserRepository.findByUserName("admin")).thenReturn(Optional.of(existing));

        AdminSeedService.SeedResult result = service().seeAdmin("admin", "irrelevant");

        assertThat(result).isEqualTo(AdminSeedService.SeedResult.ALREADY_EXISTS);

        verify(adminUserRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void grantedRole_matchesWhatSecurityConfigChecksViaHasRole() {
        // SecurityConfig uses hasRole("ADMIN"), which Spring Security resolves by
        // checking for the literal granted authority "ROLE_ADMIN". If this constant
        // ever drifts from that, admin auth breaks silently at runtime with a 403 --
        // this test pins the value so that drift fails at build time instead.
        assertThat(AdminSeedService.ADMIN_ROLE).isEqualTo("ROLE_ADMIN");
    }
}
