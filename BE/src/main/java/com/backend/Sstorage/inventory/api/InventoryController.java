package com.backend.Sstorage.inventory.api;

import com.backend.Sstorage.inventory.api.application.InventoryVoucherService;
import com.backend.Sstorage.inventory.api.dto.CreateVoucherRequest;
import com.backend.Sstorage.inventory.api.domain.InventoryVoucher;
import com.backend.Sstorage.inventory.api.domain.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryVoucherService service;

    public InventoryController(InventoryVoucherService service) {
        this.service = service;
    }

    // ==========================================
    // NHẬP KHO (IMPORTS)
    // ==========================================

    // ✅ Lấy danh sách phiếu nhập (Frontend gọi GET /api/v1/imports?page=0&size=100&sort=createdAt,desc)
    @GetMapping("/imports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> listImports(
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InventoryVoucher> page = service.listVouchers(VoucherType.INBOUND, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> createImport(@RequestBody CreateVoucherRequest request) {
        InventoryVoucher voucher = service.createVoucher(
                VoucherType.INBOUND,
                request.partnerName(),
                request.note(),
                request.lines()
        );
        return ResponseEntity.ok(voucher);
    }

    @PatchMapping("/imports/{id}/complete")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> completeImport(@PathVariable UUID id) {
        service.completeVoucher(id);
        return ResponseEntity.ok().build();
    }

    // ✅ Hủy phiếu nhập
    @PatchMapping("/imports/{id}/cancel")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> cancelImport(@PathVariable UUID id) {
        service.cancelVoucher(id);
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // XUẤT KHO (EXPORTS)
    // ==========================================

    // ✅ Lấy danh sách phiếu xuất (Frontend gọi GET /api/v1/exports?page=0&size=100&sort=createdAt,desc)
    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> listExports(
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InventoryVoucher> page = service.listVouchers(VoucherType.OUTBOUND, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> createExport(@RequestBody CreateVoucherRequest request) {
        InventoryVoucher voucher = service.createVoucher(
                VoucherType.OUTBOUND,
                request.partnerName(),
                request.note(),
                request.lines()
        );
        return ResponseEntity.ok(voucher);
    }

    @PatchMapping("/exports/{id}/complete")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> completeExport(@PathVariable UUID id) {
        service.completeVoucher(id);
        return ResponseEntity.ok().build();
    }

    // ✅ Hủy phiếu xuất
    @PatchMapping("/exports/{id}/cancel")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'WAREHOUSE_STAFF')")
    public ResponseEntity<?> cancelExport(@PathVariable UUID id) {
        service.cancelVoucher(id);
        return ResponseEntity.ok().build();
    }
}