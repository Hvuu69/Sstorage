package com.backend.Sstorage.identification.api.dto;

import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import com.backend.Sstorage.product.domain.ProductStatus;

import java.util.UUID;

public record IdentifierLookupResponse(
        UUID productId,
        String sku,
        String productName,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        UUID unitId,
        String unitCode,
        String unitName,
        String unitSymbol,
        ProductStatus status,
        UUID identifierId,
        IdentifierType identifierType,
        String identifierValue
) {
    public static IdentifierLookupResponse from(ProductIdentifier identifier) {
        var product = identifier.getProduct();
        return new IdentifierLookupResponse(product.getId(), product.getSku(), product.getName(),
                product.getCategory().getId(), product.getCategory().getCode(), product.getCategory().getName(),
                product.getUnit().getId(), product.getUnit().getCode(), product.getUnit().getName(),
                product.getUnit().getSymbol(), product.getStatus(), identifier.getId(), identifier.getType(),
                identifier.getValue());
    }
}
