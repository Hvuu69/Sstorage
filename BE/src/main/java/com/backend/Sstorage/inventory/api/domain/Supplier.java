package com.backend.Sstorage.inventory.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers", schema = "public")
public class Supplier {
    @Id
    private UUID id;

    @Column(name = "supplier_code", nullable = false, unique = true)
    private String supplierCode; // ✅ Thêm trường này

    @Column(nullable = false)
    private String name;

    private String phone;
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

    public Supplier() {}

    // ✅ Cập nhật Constructor để tự sinh mã code
    public Supplier(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.supplierCode = "NCC-" + System.currentTimeMillis();
    }

    public Supplier(UUID id, String supplierCode, String name, boolean active) {
        this.id = id;
        this.supplierCode = supplierCode;
        this.name = name;
        this.active = active;
    }

    public Supplier(UUID id, String supplierCode, String name, String phone,
                    String email, String address) {
        this.id = id;
        this.supplierCode = supplierCode;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public UUID getId() { return id; }
    public String getSupplierCode() { return supplierCode; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setName(String name) { this.name = name; }
    public void update(String name, String phone, String email, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }
    public void changeStatus(boolean active) { this.active = active; }
}
