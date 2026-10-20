package com.backend.Sstorage.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_balances", schema = "public")
public class InventoryBalance {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false)
    private long quantityOnHand;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InventoryBalance() {
    }

    public InventoryBalance(Product product) {
        this.product = product;
        this.quantityOnHand = 0;
    }

    public UUID getProductId() { return productId; }
    public long getQuantityOnHand() { return quantityOnHand; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
