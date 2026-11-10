package com.backend.Sstorage.security.user.api.dto;

import com.backend.Sstorage.security.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {}
