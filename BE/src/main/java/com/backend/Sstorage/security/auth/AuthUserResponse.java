package com.backend.Sstorage.security.auth;

import com.backend.Sstorage.security.user.AppUser;

import java.util.UUID;

public record AuthUserResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        UUID dealerId
) {
    public static AuthUserResponse from(AppUser user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().getCode().name(),
                user.getDealerId()
        );
    }
}
