package com.backend.Sstorage.inventory.inbound.domain;

import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.product.domain.Product;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "supplier_products", schema = "public")
public class SupplierProduct {
    @EmbeddedId private SupplierProductId id;
    @MapsId("supplierId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id") private Supplier supplier;
    @MapsId("productId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id") private Product product;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SupplierProduct() {}
    public SupplierProduct(Supplier supplier, Product product) {
        this.id = new SupplierProductId(supplier.getId(), product.getId());
        this.supplier = supplier;
        this.product = product;
    }
    public SupplierProductId getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public Product getProduct() { return product; }
    public boolean isActive() { return active; }
    public void changeStatus(boolean active) { this.active = active; }
}
