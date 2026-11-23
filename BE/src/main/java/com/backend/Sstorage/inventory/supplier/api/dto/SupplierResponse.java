package com.backend.Sstorage.inventory.supplier.api.dto;

import com.backend.Sstorage.inventory.api.domain.Supplier;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String code,
        String name,
        String phone,
        String email,
        String address,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getSupplierCode(), supplier.getName(),
                supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.isActive(),
                supplier.getCreatedAt(), supplier.getUpdatedAt());
    }
}
