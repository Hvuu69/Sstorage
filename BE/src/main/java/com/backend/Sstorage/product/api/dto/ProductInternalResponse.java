package com.backend.Sstorage.product.api.dto;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductInternalResponse(
        UUID id,
        String sku,
        String name,
        String description,
        ProductStatus status,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        UUID unitId,
        String unitCode,
        String unitName,
        String unitSymbol,
        Long quantityOnHand,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductInternalResponse from(Product product, Long quantityOnHand) {
        return new ProductInternalResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getStatus(), product.getCategory().getId(),
                product.getCategory().getCode(), product.getCategory().getName(), product.getUnit().getId(),
                product.getUnit().getCode(), product.getUnit().getName(), product.getUnit().getSymbol(),
                quantityOnHand, product.getCreatedAt(), product.getUpdatedAt());
    }
}
