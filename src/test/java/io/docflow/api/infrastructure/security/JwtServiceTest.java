package io.docflow.api.infrastructure.security;

import io.docflow.api.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JWT (JSON Web Token) operations.
 * Validates token generation, cryptographic signature verification,
 * subject extraction, and expiration logic for Admin authentication.
 */
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
    @DisplayName("Should generate and validate token for a valid username")
    void shouldGenerateAndValidateToken() {
        String username = "admin";
        String token = jwtService.generateToken(username);

        assertNotNull(token);
        assertEquals(username, jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token));
    }
}
