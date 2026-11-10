
package com.backend.Sstorage.inventory.alert.domain;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.security.user.AppUser;

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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_alerts", schema = "public")
public class InventoryAlert {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    // San pham lien quan den canh bao
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // ID ban ghi du bao AI, co the null
    // Database da co FK den demand_forecasts.id
    private UUID forecastId;

    // LOW_STOCK hoac OVERSTOCK
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private InventoryAlertType alertType;

    // NEW, ACKNOWLEDGED, RESOLVED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryAlertStatus status = InventoryAlertStatus.NEW;

    // So luong ton kho tai thoi diem tao canh bao
    @Column(name = "current_quantity", nullable = false)
    private long currentQuantity;

    // So luong bo sung de xuat, co the null
    @Column(name = "suggested_quantity")
    private Long suggestedQuantity;

    // Noi dung canh bao
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    // Nguoi tiep nhan canh bao
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private AppUser acknowledgedBy;

    // Thoi diem tiep nhan
    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    // Thoi diem xu ly xong
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    // Thoi diem tao canh bao
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryAlert() {
    }

    public InventoryAlert(
            Product product,
            UUID forecastId,
            InventoryAlertType alertType,
            long currentQuantity,
            Long suggestedQuantity,
            String message
    ) {

        if (product == null) {
            throw new IllegalArgumentException(
                    "San pham canh bao khong duoc de trong."
            );
        }

        if (alertType == null) {
            throw new IllegalArgumentException(
                    "Loai canh bao khong duoc de trong."
            );
        }

        if (currentQuantity < 0) {
            throw new IllegalArgumentException(
                    "So luong ton kho khong duoc am."
            );
        }

        if (suggestedQuantity != null && suggestedQuantity < 0) {
            throw new IllegalArgumentException(
                    "So luong de xuat khong duoc am."
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Noi dung canh bao khong duoc de trong."
            );
        }

        this.product = product;
        this.forecastId = forecastId;
        this.alertType = alertType;
        this.status = InventoryAlertStatus.NEW;
        this.currentQuantity = currentQuantity;
        this.suggestedQuantity = suggestedQuantity;
        this.message = message.trim();
    }

    /**
     * Quan ly tiep nhan canh bao.
     */
    public void acknowledge(
            AppUser user,
            OffsetDateTime acknowledgedAt
    ) {

        if (this.status != InventoryAlertStatus.NEW) {
            throw new IllegalStateException(
                    "Chi canh bao NEW moi duoc tiep nhan."
            );
        }

        if (user == null || acknowledgedAt == null) {
            throw new IllegalArgumentException(
                    "Thong tin nguoi tiep nhan khong hop le."
            );
        }

        this.acknowledgedBy = user;
        this.acknowledgedAt = acknowledgedAt;
        this.status = InventoryAlertStatus.ACKNOWLEDGED;
    }

    /**
     * Danh dau canh bao da xu ly.
     */
    public void resolve(OffsetDateTime resolvedAt) {

        if (this.status != InventoryAlertStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    "Canh bao phai duoc tiep nhan truoc khi xu ly."
            );
        }

        if (resolvedAt == null) {
            throw new IllegalArgumentException(
                    "Thoi diem xu ly khong duoc de trong."
            );
        }

        this.resolvedAt = resolvedAt;
        this.status = InventoryAlertStatus.RESOLVED;
    }

    // ================= GETTERS =================

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public UUID getForecastId() {
        return forecastId;
    }

    public InventoryAlertType getAlertType() {
        return alertType;
    }

    public InventoryAlertStatus getStatus() {
        return status;
    }

    public long getCurrentQuantity() {
        return currentQuantity;
    }

    public Long getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public String getMessage() {
        return message;
    }

    public AppUser getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public OffsetDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
