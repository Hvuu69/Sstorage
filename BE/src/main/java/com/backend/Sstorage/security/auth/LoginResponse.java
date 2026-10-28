
package com.backend.Sstorage.security.auth;

import java.util.UUID;
import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        Set<String> roles,
        UUID userId,
        String email,
        String fullName,
        String role,
        UUID dealerId
) {}
