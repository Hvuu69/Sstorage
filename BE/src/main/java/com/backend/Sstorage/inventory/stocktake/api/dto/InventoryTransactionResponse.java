package com.backend.Sstorage.inventory.stocktake.api.dto;

import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransaction;
import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransactionSource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID productId,
        String sku,
        String productName,
        String unitSymbol,
        long quantityDelta,
        InventoryTransactionSource source,
        UUID sourceItemId,
        String sourceReference,
        OffsetDateTime createdAt
) {
    public static InventoryTransactionResponse from(
            InventoryTransaction transaction) {

        InventoryTransactionSource source;
        UUID sourceItemId;
        String sourceReference;

        if (transaction.getStocktakeItem() != null) {
            source = InventoryTransactionSource.STOCKTAKE;
            sourceItemId = transaction.getStocktakeItem().getId();
            sourceReference = transaction.getStocktakeItem()
                    .getStocktake()
                    .getStocktakeCode();

        } else if (transaction.getInboundItemId() != null) {
            source = InventoryTransactionSource.INBOUND;
            sourceItemId = transaction.getInboundItemId();
            sourceReference = transaction.getInboundItemId().toString();

        } else {
            source = InventoryTransactionSource.OUTBOUND;
            sourceItemId = transaction.getOutboundItemId();
            sourceReference = transaction.getOutboundItemId() == null
                    ? "—"
                    : transaction.getOutboundItemId().toString();
        }

        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getSku(),
                transaction.getProduct().getName(),
                transaction.getProduct().getUnit().getSymbol(),
                transaction.getQuantityDelta(),
                source,
                sourceItemId,
                sourceReference,
                transaction.getCreatedAt()
        );
    }
}