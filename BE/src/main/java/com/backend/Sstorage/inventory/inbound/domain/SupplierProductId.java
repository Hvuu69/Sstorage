package com.backend.Sstorage.inventory.inbound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SupplierProductId implements Serializable {
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "product_id") private UUID productId;

    protected SupplierProductId() {}
    public SupplierProductId(UUID supplierId, UUID productId) {
        this.supplierId = supplierId;
        this.productId = productId;
    }
    public UUID getSupplierId() { return supplierId; }
    public UUID getProductId() { return productId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SupplierProductId that)) return false;
        return Objects.equals(supplierId, that.supplierId) && Objects.equals(productId, that.productId);
    }
    @Override public int hashCode() { return Objects.hash(supplierId, productId); }
}
