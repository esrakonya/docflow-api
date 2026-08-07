package io.docflow.api.infrastructure.security;

import io.docflow.api.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVz");
        properties.setExpirationMs(3600000);
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("Geçerli bir kullanıcı adı için token üretmeli ve doğruyabilmeli")
    void shouldGenerateAndValidateToken() {
        String username = "admin";
        String token = jwtService.generateToken(username);

        assertNotNull(token);
        assertEquals(username, jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token));
    }
}
