package com.backend.Sstorage.inventory.stocktake.domain;

import com.backend.Sstorage.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions", schema = "public")
public class InventoryTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "inbound_item_id")
    private UUID inboundItemId;

    @Column(name = "outbound_item_id")
    private UUID outboundItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_item_id")
    private StocktakeItem stocktakeItem;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryTransaction() {
    }

    public InventoryTransaction(
            Product product,
            StocktakeItem stocktakeItem,
            long quantityDelta
    ) {
        if (quantityDelta == 0) {
            throw new IllegalArgumentException(
                    "Biến động tồn kho phải khác 0."
            );
        }

        this.product = product;
        this.stocktakeItem = stocktakeItem;
        this.quantityDelta = quantityDelta;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public UUID getInboundItemId() {
        return inboundItemId;
    }

    public UUID getOutboundItemId() {
        return outboundItemId;
    }

    public StocktakeItem getStocktakeItem() {
        return stocktakeItem;
    }

    public long getQuantityDelta() {
        return quantityDelta;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}