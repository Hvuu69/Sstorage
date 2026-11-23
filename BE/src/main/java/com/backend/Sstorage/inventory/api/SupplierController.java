package com.backend.Sstorage.inventory.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.inventory.supplier.api.dto.*;
import com.backend.Sstorage.inventory.supplier.application.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private static final Set<String> ALLOWED_SORTS = Set.of("supplierCode", "name", "active", "createdAt", "updatedAt");
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PageResponse<SupplierResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return PageResponse.from(service.list(search, active,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public SupplierResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest request) {
        SupplierResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public SupplierResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody SupplierStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
