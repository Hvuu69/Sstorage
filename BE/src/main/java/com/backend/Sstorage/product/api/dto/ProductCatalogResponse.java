package com.backend.Sstorage.product.api.dto;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCatalogResponse(
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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductCatalogResponse from(Product product) {
        return new ProductCatalogResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), product.getStatus(), product.getCategory().getId(),
                product.getCategory().getCode(), product.getCategory().getName(), product.getUnit().getId(),
                product.getUnit().getCode(), product.getUnit().getName(), product.getUnit().getSymbol(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
