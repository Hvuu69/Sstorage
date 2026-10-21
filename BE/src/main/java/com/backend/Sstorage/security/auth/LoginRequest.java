package com.backend.Sstorage.security.auth;

public record LoginRequest(
        String username,
        String password
) {
}