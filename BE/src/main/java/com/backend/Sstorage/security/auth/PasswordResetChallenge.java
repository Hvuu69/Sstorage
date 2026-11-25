package com.backend.Sstorage.security.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "password_reset_challenges", schema = "public")
public class PasswordResetChallenge {
    @Id
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "reset_token_hash", length = 100)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    protected PasswordResetChallenge() {}

    public PasswordResetChallenge(String email, String otpHash, Instant otpExpiresAt, Instant requestedAt) {
        this.email = email;
        this.otpHash = otpHash;
        this.otpExpiresAt = otpExpiresAt;
        this.requestedAt = requestedAt;
        this.attempts = 0;
    }

    public String getEmail() { return email; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getRequestedAt() { return requestedAt; }
    public int getAttempts() { return attempts; }
    public String getResetTokenHash() { return resetTokenHash; }
    public Instant getResetTokenExpiresAt() { return resetTokenExpiresAt; }
    public void replaceOtp(String hash, Instant expiresAt, Instant now) {
        otpHash = hash; otpExpiresAt = expiresAt; requestedAt = now; attempts = 0;
        resetTokenHash = null; resetTokenExpiresAt = null;
    }
    public void incrementAttempts() { attempts++; }
    public void authorizeReset(String tokenHash, Instant expiresAt) {
        resetTokenHash = tokenHash; resetTokenExpiresAt = expiresAt; otpHash = "";
    }
}
