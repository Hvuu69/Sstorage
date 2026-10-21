
package com.backend.Sstorage.security.auth;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        Set<String> roles
) {}
