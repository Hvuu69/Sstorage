package com.backend.Sstorage.dealerrequest.domain;

import com.backend.Sstorage.dealer.domain.Dealer;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.security.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_requests", schema = "public")
public class StockRequest {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "request_code", nullable = false, unique = true, length = 30)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    private Dealer dealer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private AppUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockRequestStatus status = StockRequestStatus.PENDING;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.PERSIST)
    private List<StockRequestItem> items = new ArrayList<>();

    protected StockRequest() {
    }

    public StockRequest(String requestCode, Dealer dealer, AppUser requestedBy, String note) {
        this.requestCode = requestCode;
        this.dealer = dealer;
        this.requestedBy = requestedBy;
        this.note = note;
    }

    public void addItem(Product product, long quantity) {
        items.add(new StockRequestItem(this, product, quantity));
    }

    public void review(StockRequestStatus status, AppUser reviewer, String reviewNote, OffsetDateTime reviewedAt) {
        this.status = status;
        this.reviewedBy = reviewer;
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
    }

    public UUID getId() { return id; }
    public String getRequestCode() { return requestCode; }
    public Dealer getDealer() { return dealer; }
    public AppUser getRequestedBy() { return requestedBy; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public StockRequestStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getReviewNote() { return reviewNote; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<StockRequestItem> getItems() { return Collections.unmodifiableList(items); }
}
