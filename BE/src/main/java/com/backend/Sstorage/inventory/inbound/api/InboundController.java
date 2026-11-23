package com.backend.Sstorage.inventory.inbound.api;

import com.backend.Sstorage.inventory.inbound.api.InboundDtos.*;
import com.backend.Sstorage.inventory.inbound.application.InboundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InboundController {
    private final InboundService service;
    public InboundController(InboundService service) { this.service = service; }

    @GetMapping("/suppliers/active")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public PageResponse<SupplierSummary> activeSuppliers(@RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.activeSuppliers(search, pageable);
    }

    @GetMapping("/suppliers/{supplierId}/products")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public PageResponse<SupplierProductResponse> supplierProducts(@PathVariable UUID supplierId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "product.name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.supplierProducts(supplierId, search, pageable);
    }

    @GetMapping("/imports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public PageResponse<ReceiptResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/imports/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ReceiptResponse get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/imports")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ResponseEntity<ReceiptResponse> create(@Valid @RequestBody SaveRequest request) {
        ReceiptResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/imports/" + response.id())).body(response);
    }

    @PutMapping("/imports/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ReceiptResponse update(@PathVariable UUID id, @Valid @RequestBody SaveRequest request) { return service.update(id, request); }

    @PostMapping("/imports/{id}/scan")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ReceiptResponse scan(@PathVariable UUID id, @Valid @RequestBody ScanRequest request) { return service.scan(id, request.code()); }

    @PatchMapping("/imports/{id}/complete")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ReceiptResponse confirm(@PathVariable UUID id) { return service.confirm(id); }

    @PatchMapping("/imports/{id}/cancel")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
    public ReceiptResponse cancel(@PathVariable UUID id) { return service.cancel(id); }
}
