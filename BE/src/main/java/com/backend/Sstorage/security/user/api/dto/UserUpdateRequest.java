package com.backend.Sstorage.security.user.api.dto;

import com.backend.Sstorage.security.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserUpdateRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Role role,
        UUID dealerId,
        @Size(max = 72) String newPassword
) {}
