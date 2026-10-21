package com.backend.Sstorage.security.auth;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LoginRequest(
        @JsonAlias("username") String email,
        String password
) {
}
