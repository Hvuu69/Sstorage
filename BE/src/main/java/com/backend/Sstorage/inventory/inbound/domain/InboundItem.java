package com.backend.Sstorage.inventory.inbound.domain;

import com.backend.Sstorage.product.domain.Product;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inbound_items", schema = "public",
       uniqueConstraints = @UniqueConstraint(columnNames = {"inbound_id", "product_id"}))
public class InboundItem {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inbound_id") private InboundReceipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false) private long quantity;

    protected InboundItem() {}
    public InboundItem(Product product, long quantity) { this.product = product; this.quantity = quantity; }
    void attach(InboundReceipt receipt) { this.receipt = receipt; }
    public void increment() { quantity = Math.addExact(quantity, 1L); }
    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public long getQuantity() { return quantity; }
}
