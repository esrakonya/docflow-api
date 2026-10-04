package io.docflow.api.infrastructure.security;

import io.docflow.api.core.admin.service.AdminSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminUserInitializerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, AdminUserInitializer.class);

    @Test
    void initializer_passesConfiguredCredentialsToAdminSeedService() {
        contextRunner
                .withPropertyValues(
                        "app.admin.username=test-admin",
                        "app.admin.password=test-password123"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    AdminSeedService adminSeedService = context.getBean(AdminSeedService.class);
                    AdminUserInitializer initializer = context.getBean(AdminUserInitializer.class);

                    when(adminSeedService.seeAdmin("test-admin", "test-password123"))
                            .thenReturn(AdminSeedService.SeedResult.ALREADY_EXISTS);

                    initializer.run();

                    verify(adminSeedService)
                            .seeAdmin("test-admin", "test-password123");
                });
    }


    @Test
    void initializer_requiresExplicitUsernameProperty() throws NoSuchFieldException {
        Value value = AdminUserInitializer.class
                .getDeclaredField("adminUsername")
                .getAnnotation(Value.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isEqualTo("${app.admin.username}");
    }

    @Test
    void initializer_requiresExplicitPasswordProperty() throws NoSuchFieldException {
        Value value = AdminUserInitializer.class
                .getDeclaredField("adminPassword")
                .getAnnotation(Value.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isEqualTo("${app.admin.password}");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AdminSeedService adminSeedService() {
            return mock(AdminSeedService.class);
        }
    }
}
