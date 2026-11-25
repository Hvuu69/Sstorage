package com.backend.Sstorage.security.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, String> {}
