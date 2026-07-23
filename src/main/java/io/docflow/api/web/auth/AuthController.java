package io.docflow.api.web.auth;

import io.docflow.api.core.admin.dto.AdminLoginRequest;
import io.docflow.api.core.admin.service.AdminAuthService;
import io.docflow.api.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAuthService adminAuthService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody AdminLoginRequest request) {
        adminAuthService.authenticate(request);
        String token = jwtService.generateToken(request.username());
        return ResponseEntity.ok(Map.of("token", token));
    }
}
