package com.backend.Sstorage.security.user.api.dto;

import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.UserStatus;

import java.util.UUID;

public record UserAccountResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        UserStatus status,
        UUID dealerId
) {
    public static UserAccountResponse from(AppUser user) {
        return new UserAccountResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole().getCode().name(),
                user.getStatus(),
                user.getDealerId()
        );
    }
}
