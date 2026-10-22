package com.backend.Sstorage.dealer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dealers", schema = "public")
public class Dealer {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "dealer_code", nullable = false, unique = true, length = 30)
    private String dealerCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Dealer() {
    }

    public Dealer(String dealerCode, String name, String phone, String email, String address) {
        this.dealerCode = dealerCode;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public void update(String name, String phone, String email, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public void changeStatus(boolean active) {
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getDealerCode() { return dealerCode; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
