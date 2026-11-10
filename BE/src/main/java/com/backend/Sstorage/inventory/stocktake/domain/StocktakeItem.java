package com.backend.Sstorage.inventory.stocktake.domain;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.security.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stocktake_items", schema = "public")
public class StocktakeItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    // Phien kiem ke
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    // San pham duoc kiem ke
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Nhan vien thuc hien kiem dem
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by")
    private AppUser countedBy;

    // So luong tren he thong tai thoi diem snapshot
    @Column(name = "system_quantity", nullable = false)
    private long systemQuantity;

    // So luong kiem dem thuc te
    // Null neu chua kiem dem
    @Column(name = "actual_quantity")
    private Long actualQuantity;

    // Thoi diem hoan thanh kiem dem
    @Column(name = "counted_at")
    private OffsetDateTime countedAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected StocktakeItem() {
    }

    public StocktakeItem(
            Stocktake stocktake,
            Product product,
            long systemQuantity
    ) {
        if (systemQuantity < 0) {
            throw new IllegalArgumentException(
                    "So luong tren he thong khong duoc am"
            );
        }

        this.stocktake = stocktake;
        this.product = product;
        this.systemQuantity = systemQuantity;
        this.actualQuantity = null;
    }

    /**
     * Ghi nhan so luong kiem dem thuc te.
     */
    public void recordCount(
            long actualQuantity,
            AppUser countedBy,
            OffsetDateTime countedAt,
            String note
    ) {
        if (actualQuantity < 0) {
            throw new IllegalArgumentException(
                    "So luong kiem dem khong duoc am"
            );
        }

        if (countedBy == null) {
            throw new IllegalArgumentException(
                    "Nhan vien kiem dem khong duoc de trong"
            );
        }

        if (countedAt == null) {
            throw new IllegalArgumentException(
                    "Thoi diem kiem dem khong duoc de trong"
            );
        }

        this.actualQuantity = actualQuantity;
        this.countedBy = countedBy;
        this.countedAt = countedAt;
        this.note = note;
    }

    /**
     * Kiem tra da dem hay chua.
     */
    public boolean isCounted() {
        return actualQuantity != null;
    }

    /**
     * Tinh chenh lech:
     * Thuc te - He thong.
     *
     * Duong: Thua hang.
     * Am: Thieu hang.
     * Bang 0: Khop so lieu.
     */
    public Long getDifference() {
        if (actualQuantity == null) {
            return null;
        }

        return actualQuantity - systemQuantity;
    }

    public UUID getId() {
        return id;
    }

    public Stocktake getStocktake() {
        return stocktake;
    }

    public Product getProduct() {
        return product;
    }

    public AppUser getCountedBy() {
        return countedBy;
    }

    public long getSystemQuantity() {
        return systemQuantity;
    }

    public Long getActualQuantity() {
        return actualQuantity;
    }

    public OffsetDateTime getCountedAt() {
        return countedAt;
    }

    public String getNote() {
        return note;
    }
}