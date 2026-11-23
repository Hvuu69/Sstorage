package com.backend.Sstorage.product.api.dto;

import com.backend.Sstorage.inventory.inbound.domain.SupplierProduct;

import java.util.UUID;

public record ProductSupplierResponse(
        UUID id,
        String supplierCode,
        String name,
        String supplierStatus,
        String linkStatus
) {
    public static ProductSupplierResponse from(SupplierProduct link) {
        return new ProductSupplierResponse(
                link.getSupplier().getId(),
                link.getSupplier().getSupplierCode(),
                link.getSupplier().getName(),
                link.getSupplier().isActive() ? "ACTIVE" : "INACTIVE",
                link.isActive() ? "ACTIVE" : "INACTIVE"
        );
    }
}
