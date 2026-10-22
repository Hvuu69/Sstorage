package com.backend.Sstorage.dealerrequest.api.dto;

import com.backend.Sstorage.dealerrequest.domain.StockRequestItem;

import java.util.UUID;

public record StockRequestItemResponse(
        UUID productId,
        String sku,
        String productName,
        String unitSymbol,
        long quantity
) {
    public static StockRequestItemResponse from(StockRequestItem item) {
        return new StockRequestItemResponse(item.getProduct().getId(), item.getProduct().getSku(),
                item.getProduct().getName(), item.getProduct().getUnit().getSymbol(), item.getQuantity());
    }
}
