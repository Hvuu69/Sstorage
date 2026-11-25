package com.backend.Sstorage.security.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PasswordResetRequests {
    private PasswordResetRequests() {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Pattern(regexp = "\\d{6}") String otp) {}
    public record CompleteRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank String resetToken,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank String confirmPassword) {}
}
