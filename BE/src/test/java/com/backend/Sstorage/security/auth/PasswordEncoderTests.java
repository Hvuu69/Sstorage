package com.backend.Sstorage.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTests {

    private static final String DEMO_HASH =
            "$2a$10$9o6IaEwUqM0bpXEANdaQWe700AAaIc2RJInu4WetqoYemV95Y3XFe";

    @Test
    void demoPasswordMatchesStoredBcryptHash() {
        assertThat(new BCryptPasswordEncoder().matches("Demo@123456", DEMO_HASH)).isTrue();
    }
}
