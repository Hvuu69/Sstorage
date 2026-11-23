package com.backend.Sstorage.inventory.inbound.domain;

import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.security.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts", schema = "public")
public class InboundReceipt {
    @Id @GeneratedValue private UUID id;
    @Column(name = "receipt_code", nullable = false, unique = true, length = 30) private String receiptCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "confirmed_by") private AppUser confirmedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InboundStatus status = InboundStatus.DRAFT;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "confirmed_at") private OffsetDateTime confirmedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InboundItem> items = new ArrayList<>();

    protected InboundReceipt() {}
    public InboundReceipt(String receiptCode, Supplier supplier, AppUser createdBy, String note) {
        this.receiptCode = receiptCode;
        this.supplier = supplier;
        this.createdBy = createdBy;
        this.note = note;
    }
    public void replaceDraft(Supplier supplier, String note, List<InboundItem> newItems) {
        requireDraft();
        this.supplier = supplier;
        this.note = note;
        this.items.clear();
        newItems.forEach(this::addItem);
    }
    public void addItem(InboundItem item) { items.add(item); item.attach(this); }
    public void confirm(AppUser actor, OffsetDateTime at) { requireDraft(); status = InboundStatus.CONFIRMED; confirmedBy = actor; confirmedAt = at; }
    public void cancel() { requireDraft(); status = InboundStatus.CANCELLED; }
    private void requireDraft() {
        if (status != InboundStatus.DRAFT) throw new IllegalStateException("Phiếu nhập không còn ở trạng thái nháp.");
    }
    public UUID getId() { return id; }
    public String getReceiptCode() { return receiptCode; }
    public Supplier getSupplier() { return supplier; }
    public AppUser getCreatedBy() { return createdBy; }
    public AppUser getConfirmedBy() { return confirmedBy; }
    public InboundStatus getStatus() { return status; }
    public String getNote() { return note; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<InboundItem> getItems() { return items; }
}
