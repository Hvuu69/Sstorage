package com.backend.Sstorage.security.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody PasswordResetRequests.EmailRequest body) {
        service.requestOtp(body.email());
        return ResponseEntity.ok(Map.of("message", "Nếu email đã đăng ký, mã xác thực sẽ được gửi đến hộp thư của bạn."));
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@Valid @RequestBody PasswordResetRequests.VerifyRequest body) {
        return Map.of("resetToken", service.verifyOtp(body.email(), body.otp()));
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, String>> complete(@Valid @RequestBody PasswordResetRequests.CompleteRequest body) {
        service.complete(body.email(), body.resetToken(), body.password(), body.confirmPassword());
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đổi thành công."));
    }
}
