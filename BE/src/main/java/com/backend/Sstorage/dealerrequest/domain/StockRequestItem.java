package com.backend.Sstorage.dealerrequest.domain;

import com.backend.Sstorage.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "stock_request_items", schema = "public",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "product_id"}))
public class StockRequestItem {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private StockRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private long quantity;

    protected StockRequestItem() {
    }

    StockRequestItem(StockRequest request, Product product, long quantity) {
        this.request = request;
        this.product = product;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public long getQuantity() { return quantity; }
}
