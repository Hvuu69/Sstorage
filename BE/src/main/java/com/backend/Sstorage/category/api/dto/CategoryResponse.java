package com.backend.Sstorage.category.api.dto;

import com.backend.Sstorage.category.domain.Category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
    }
}
