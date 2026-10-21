
package com.backend.Sstorage.security.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        if (request.email() == null
                || request.email().isBlank()
                || request.password() == null
                || request.password().isBlank()) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "message",
                    "Vui lòng nhập email và mật khẩu"
                )
            );
        }

        try {
            return ResponseEntity.ok(
                authService.login(request)
            );
        } catch (AuthenticationException ex) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "message",
                    "Tên đăng nhập hoặc mật khẩu không đúng"
                ));
        }
    }
}
