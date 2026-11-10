package com.backend.Sstorage.inventory.stocktake.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stocktakes", schema = "public")
public class Stocktake {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(
            name = "stocktake_code",
            nullable = false,
            unique = true,
            length = 30
    )
    private String stocktakeCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StocktakeStatus status = StocktakeStatus.DRAFT;

    @Column(name = "snapshot_at", nullable = false)
    private OffsetDateTime snapshotAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Stocktake() {
    }

    public Stocktake(
            String stocktakeCode,
            AppUser createdBy,
            String note,
            OffsetDateTime snapshotAt
    ) {
        this.stocktakeCode = stocktakeCode;
        this.createdBy = createdBy;
        this.note = note;
        this.snapshotAt = snapshotAt;
        this.status = StocktakeStatus.DRAFT;
    }

    // Bat dau kiem ke
    public void start() {
        this.status = StocktakeStatus.IN_PROGRESS;
    }

    // Gui ket qua kiem ke cho quan ly
    public void submitForConfirmation() {
        this.status = StocktakeStatus.PENDING_CONFIRMATION;
    }

    // Yeu cau kiem ke lai
    public void requireRecount() {
        this.status = StocktakeStatus.RECOUNT_REQUIRED;
    }

    // Lam moi snapshot khi bat dau kiem ke lai
    public void refreshSnapshot(OffsetDateTime snapshotAt) {
        this.snapshotAt = snapshotAt;
        this.status = StocktakeStatus.IN_PROGRESS;
    }

    // Quan ly phe duyet ket qua
    public void complete(
            AppUser approver,
            OffsetDateTime approvedAt
    ) {
        this.approvedBy = approver;
        this.approvedAt = approvedAt;
        this.status = StocktakeStatus.COMPLETED;
    }

    // Huy phien kiem ke
    public void cancel() {
        this.status = StocktakeStatus.CANCELLED;
    }

    public void updateNote(String note) {
        this.note = note;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public String getStocktakeCode() {
        return stocktakeCode;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public AppUser getApprovedBy() {
        return approvedBy;
    }

    public StocktakeStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSnapshotAt() {
        return snapshotAt;
    }

    public String getNote() {
        return note;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}