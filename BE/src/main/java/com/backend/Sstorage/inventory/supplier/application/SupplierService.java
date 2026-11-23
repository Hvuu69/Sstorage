package com.backend.Sstorage.inventory.supplier.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.common.text.BusinessCodeNormalizer;
import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.inventory.api.infrastructure.SupplierRepository;
import com.backend.Sstorage.inventory.supplier.api.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierService {
    private final SupplierRepository repository;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public SupplierService(SupplierRepository repository, AuditPort auditPort, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Supplier> specification = (root, query, builder) -> builder.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("supplierCode")), pattern),
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("email")), pattern)));
        }
        if (active != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("active"), active));
        }
        return repository.findAll(specification, pageable).map(SupplierResponse::from);
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.from(find(id));
    }

    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        String code = BusinessCodeNormalizer.normalize(request.code());
        ensureUniqueCode(code);
        Supplier supplier = repository.saveAndFlush(new Supplier(UUID.randomUUID(), code, request.name().trim(),
                normalizeOptional(request.phone()), request.email().trim(), normalizeOptional(request.address())));
        audit("SUPPLIER_CREATED", supplier.getId(), null, snapshot(supplier));
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierUpdateRequest request) {
        Supplier supplier = find(id);
        Map<String, Object> oldValues = snapshot(supplier);
        supplier.update(request.name().trim(), normalizeOptional(request.phone()), request.email().trim(),
                normalizeOptional(request.address()));
        repository.flush();
        audit("SUPPLIER_UPDATED", supplier.getId(), oldValues, snapshot(supplier));
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse changeStatus(UUID id, SupplierStatusRequest request) {
        Supplier supplier = find(id);
        Map<String, Object> oldValues = snapshot(supplier);
        supplier.changeStatus(request.active());
        repository.flush();
        audit("SUPPLIER_STATUS_CHANGED", supplier.getId(), oldValues, snapshot(supplier));
        return SupplierResponse.from(supplier);
    }

    private Supplier find(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp " + id + "."));
    }

    private void ensureUniqueCode(String code) {
        if (repository.existsBySupplierCodeIgnoreCase(code)) {
            throw new BusinessConflictException("SUPPLIER_CODE_EXISTS", "Mã nhà cung cấp đã tồn tại.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Supplier supplier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", supplier.getSupplierCode());
        values.put("name", supplier.getName());
        values.put("phone", supplier.getPhone());
        values.put("email", supplier.getEmail());
        values.put("address", supplier.getAddress());
        values.put("active", supplier.isActive());
        return values;
    }

    private void audit(String action, UUID id, Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "SUPPLIER", id, actorProvider.currentActor(), oldValues, newValues));
    }
}
