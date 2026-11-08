
package com.backend.Sstorage.inventory.alert.domain;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.security.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_policies", schema = "public")
public class InventoryPolicy {

    // product_id dong thoi la PK va FK
    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    // Moi san pham co mot chinh sach ton kho
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // So luong ton kho toi thieu
    @Column(name = "minimum_quantity", nullable = false)
    private long minimumQuantity;

    // So luong ton kho toi da
    // Cho phep null neu khong gioi han
    @Column(name = "maximum_quantity")
    private Long maximumQuantity;

    // Luong hang du tru an toan
    @Column(name = "safety_stock", nullable = false)
    private long safetyStock;

    // Thoi gian cung ung tinh theo ngay
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    // Nguoi cap nhat chinh sach
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    // Thoi diem cap nhat
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InventoryPolicy() {
    }

    public InventoryPolicy(
            Product product,
            long minimumQuantity,
            Long maximumQuantity,
            long safetyStock,
            int leadTimeDays,
            AppUser updatedBy
    ) {

        validate(
                minimumQuantity,
                maximumQuantity,
                safetyStock,
                leadTimeDays
        );

        this.product = product;
        this.minimumQuantity = minimumQuantity;
        this.maximumQuantity = maximumQuantity;
        this.safetyStock = safetyStock;
        this.leadTimeDays = leadTimeDays;
        this.updatedBy = updatedBy;
    }

    /**
     * Cap nhat chinh sach ton kho.
     */
    public void update(
            long minimumQuantity,
            Long maximumQuantity,
            long safetyStock,
            int leadTimeDays,
            AppUser updatedBy
    ) {

        validate(
                minimumQuantity,
                maximumQuantity,
                safetyStock,
                leadTimeDays
        );

        this.minimumQuantity = minimumQuantity;
        this.maximumQuantity = maximumQuantity;
        this.safetyStock = safetyStock;
        this.leadTimeDays = leadTimeDays;
        this.updatedBy = updatedBy;
    }

    /**
     * Kiem tra san pham co nguy co thieu hang.
     */
    public boolean isLowStock(long currentQuantity) {
        return currentQuantity < minimumQuantity;
    }

    /**
     * Kiem tra san pham co ton kho vuot muc cho phep.
     */
    public boolean isOverstock(long currentQuantity) {
        return maximumQuantity != null
                && currentQuantity > maximumQuantity;
    }

    /**
     * So luong can bo sung de dat muc toi thieu.
     *
     * Day la muc de xuat co ban, chua bao gom
     * nhu cau du bao AI trong thoi gian cung ung.
     */
    public long getBasicReplenishmentQuantity(
            long currentQuantity
    ) {

        if (currentQuantity < 0) {
            throw new IllegalArgumentException(
                    "So luong ton kho khong duoc am."
            );
        }

        if (currentQuantity >= minimumQuantity) {
            return 0L;
        }

        return minimumQuantity - currentQuantity;
    }

    private void validate(
            long minimumQuantity,
            Long maximumQuantity,
            long safetyStock,
            int leadTimeDays
    ) {

        if (minimumQuantity < 0) {
            throw new IllegalArgumentException(
                    "Ton kho toi thieu khong duoc am."
            );
        }

        if (maximumQuantity != null && maximumQuantity < 0) {
            throw new IllegalArgumentException(
                    "Ton kho toi da khong duoc am."
            );
        }

        if (maximumQuantity != null
                && maximumQuantity < minimumQuantity) {

            throw new IllegalArgumentException(
                    "Ton kho toi da phai lon hon hoac bang ton kho toi thieu."
            );
        }

        if (safetyStock < 0) {
            throw new IllegalArgumentException(
                    "Ton kho an toan khong duoc am."
            );
        }

        if (leadTimeDays < 0) {
            throw new IllegalArgumentException(
                    "Thoi gian cung ung khong duoc am."
            );
        }
    }

    // ================= GETTERS =================

    public UUID getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public long getMinimumQuantity() {
        return minimumQuantity;
    }

    public Long getMaximumQuantity() {
        return maximumQuantity;
    }

    public long getSafetyStock() {
        return safetyStock;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public AppUser getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
