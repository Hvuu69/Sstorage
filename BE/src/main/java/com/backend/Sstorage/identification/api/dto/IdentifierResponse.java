package com.backend.Sstorage.identification.api.dto;

import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdentifierResponse(
        UUID id,
        UUID productId,
        IdentifierType identifierType,
        String identifierValue,
        boolean active,
        OffsetDateTime createdAt
) {
    public static IdentifierResponse from(ProductIdentifier identifier) {
        return new IdentifierResponse(identifier.getId(), identifier.getProduct().getId(), identifier.getType(),
                identifier.getValue(), identifier.isActive(), identifier.getCreatedAt());
    }
}
