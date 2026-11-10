package com.backend.Sstorage.inventory.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_vouchers")
public class InventoryVoucher {
    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    private VoucherType type;

    @Enumerated(EnumType.STRING)
    private VoucherStatus status;

    private String partnerName;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // ✅ Đã thêm fetch = FetchType.EAGER để tránh lỗi "no session" khi serialize JSON
    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<VoucherLine> lines = new ArrayList<>();

    public InventoryVoucher() {}

    public InventoryVoucher(UUID id, String code, VoucherType type, String partnerName, String note) {
        this.id = id;
        this.voucherCode = code;
        this.type = type;
        this.status = VoucherStatus.DRAFT;
        this.partnerName = partnerName;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public void addLine(VoucherLine line) {
        lines.add(line);
        line.setVoucher(this);
    }

    public void complete() {
        if (this.status != VoucherStatus.DRAFT) throw new IllegalStateException("Chỉ phiếu nháp mới được xác nhận");
        this.status = VoucherStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == VoucherStatus.COMPLETED) throw new IllegalStateException("Không thể hủy phiếu đã hoàn tất");
        this.status = VoucherStatus.CANCELLED;
    }

    // ==========================================
    // GETTERS — MAP SANG JSON KHỚP VỚI FRONTEND
    // ==========================================
    public UUID getId() { return id; }

    @JsonProperty("code")
    public String getVoucherCode() { return voucherCode; }

    @JsonProperty("type")
    public VoucherType getType() { return type; }

    @JsonProperty("status")
    public VoucherStatus getStatus() { return status; }

    @JsonProperty("partner")
    public String getPartnerName() { return partnerName; }

    @JsonProperty("note")
    public String getNote() { return note; }

    @JsonProperty("createdAt")
    public LocalDateTime getCreatedAt() { return createdAt; }

    @JsonProperty("completedAt")
    public LocalDateTime getCompletedAt() { return completedAt; }

    @JsonProperty("items")
    public List<VoucherLine> getLines() { return lines; }

    @JsonProperty("totalQuantity")
    public long getTotalQuantity() {
        if (lines == null) return 0;
        return lines.stream().mapToLong(VoucherLine::getQuantity).sum();
    }

    public boolean isCompleted() {
        return this.status == VoucherStatus.COMPLETED;
    }
}