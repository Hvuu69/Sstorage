package com.backend.Sstorage.inventory.api.domain;

import com.backend.Sstorage.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "voucher_lines")
public class VoucherLine {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    @JsonIgnore // Tránh vòng lặp vô hạn khi serialize JSON
    private InventoryVoucher voucher;

    // ✅ Đã đổi LAZY thành EAGER để nạp luôn Product khi lấy VoucherLine
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    private int quantity;

    public VoucherLine() {}

    public VoucherLine(UUID id, Product product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }

    void setVoucher(InventoryVoucher voucher) { this.voucher = voucher; }

    // ==========================================
    // GETTERS CHO FRONTEND
    // ==========================================

    @JsonProperty("id")
    public UUID getId() { return id; }

    @JsonProperty("name")
    public String getProductName() {
        return (product != null) ? product.getName() : null;
    }

    @JsonProperty("sku")
    public String getProductSku() {
        return (product != null) ? product.getSku() : null;
    }

    @JsonProperty("unit")
    public String getProductUnit() {
        // ✅ Bọc try-catch để đảm bảo không bao giờ lỗi LazyLoad khi lấy Unit
        try {
            return (product != null && product.getUnit() != null) ? product.getUnit().getSymbol() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @JsonProperty("quantity")
    public int getQuantity() { return quantity; }

    /**
     * Ẩn object Product gốc khỏi JSON để tránh lỗi LazyInitializationException
     * và giảm kích thước payload. Frontend chỉ cần name, sku, unit, quantity.
     */
    @JsonIgnore
    public Product getProduct() { return product; }
}