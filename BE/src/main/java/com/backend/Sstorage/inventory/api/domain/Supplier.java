package com.backend.Sstorage.inventory.api.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    private UUID id;

    @Column(name = "supplier_code", nullable = false, unique = true)
    private String supplierCode; // ✅ Thêm trường này

    @Column(nullable = false)
    private String name;

    public Supplier() {}

    // ✅ Cập nhật Constructor để tự sinh mã code
    public Supplier(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.supplierCode = "NCC-" + System.currentTimeMillis();
    }

    public UUID getId() { return id; }
    public String getSupplierCode() { return supplierCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}