package com.backend.Sstorage.identification.domain;

import com.backend.Sstorage.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_identifiers", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_identifiers_identifier_type_identifier_value",
                columnNames = {"identifier_type", "identifier_value"}))
public class ProductIdentifier {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 10)
    private IdentifierType type;

    @Column(name = "identifier_value", nullable = false, length = 255)
    private String value;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ProductIdentifier() {
    }

    public ProductIdentifier(Product product, IdentifierType type, String value) {
        this.product = product;
        this.type = type;
        this.value = value;
    }

    public void changeStatus(boolean active) {
        this.active = active;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public IdentifierType getType() { return type; }
    public String getValue() { return value; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
