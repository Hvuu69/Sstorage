package com.backend.Sstorage.unit.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.common.text.BusinessCodeNormalizer;
import com.backend.Sstorage.unit.api.dto.UnitCreateRequest;
import com.backend.Sstorage.unit.api.dto.UnitResponse;
import com.backend.Sstorage.unit.api.dto.UnitUpdateRequest;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UnitService {
    private final UnitRepository repository;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public UnitService(UnitRepository repository, AuditPort auditPort, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<UnitResponse> list(String search, Pageable pageable) {
        Specification<Unit> specification = searchSpecification(search);
        if (specification == null) {
            specification = (root, query, builder) -> builder.conjunction();
        }
        return repository.findAll(specification, pageable).map(UnitResponse::from);
    }

    @Transactional(readOnly = true)
    public UnitResponse get(UUID id) {
        return UnitResponse.from(find(id));
    }

    @Transactional
    public UnitResponse create(UnitCreateRequest request) {
        String code = BusinessCodeNormalizer.normalize(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BusinessConflictException("UNIT_CODE_EXISTS", "Mã đơn vị đã tồn tại.");
        }
        Unit unit = repository.save(new Unit(code, request.name().trim(), request.symbol().trim()));
        audit("UNIT_CREATED", unit.getId(), null, snapshot(unit));
        return UnitResponse.from(unit);
    }

    @Transactional
    public UnitResponse update(UUID id, UnitUpdateRequest request) {
        Unit unit = find(id);
        Map<String, Object> oldValues = snapshot(unit);
        unit.update(request.name().trim(), request.symbol().trim());
        audit("UNIT_UPDATED", unit.getId(), oldValues, snapshot(unit));
        return UnitResponse.from(unit);
    }

    private Unit find(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("UNIT_NOT_FOUND", "Không tìm thấy đơn vị " + id + "."));
    }

    private Specification<Unit> searchSpecification(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("code")), pattern),
                builder.like(builder.lower(root.get("name")), pattern),
                builder.like(builder.lower(root.get("symbol")), pattern));
    }

    private Map<String, Object> snapshot(Unit unit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", unit.getCode());
        values.put("name", unit.getName());
        values.put("symbol", unit.getSymbol());
        return values;
    }

    private void audit(String action, UUID id, Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "UNIT", id, actorProvider.currentActor(), oldValues, newValues));
    }
}
