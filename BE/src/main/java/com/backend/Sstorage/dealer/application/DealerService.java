package com.backend.Sstorage.dealer.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.common.text.BusinessCodeNormalizer;
import com.backend.Sstorage.dealer.api.dto.DealerCreateRequest;
import com.backend.Sstorage.dealer.api.dto.DealerResponse;
import com.backend.Sstorage.dealer.api.dto.DealerStatusRequest;
import com.backend.Sstorage.dealer.api.dto.DealerUpdateRequest;
import com.backend.Sstorage.dealer.domain.Dealer;
import com.backend.Sstorage.dealer.infrastructure.DealerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DealerService implements DealerStatusPort {
    private final DealerRepository repository;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public DealerService(DealerRepository repository, AuditPort auditPort, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<DealerResponse> list(String search, Boolean active, Pageable pageable) {
        Specification<Dealer> specification = (root, query, builder) -> builder.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("dealerCode")), pattern),
                    builder.like(builder.lower(root.get("name")), pattern)));
        }
        if (active != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("active"), active));
        }
        return repository.findAll(specification, pageable).map(DealerResponse::from);
    }

    @Transactional(readOnly = true)
    public DealerResponse get(UUID id) {
        return DealerResponse.from(find(id));
    }

    @Transactional
    public DealerResponse create(DealerCreateRequest request) {
        String dealerCode = BusinessCodeNormalizer.normalize(request.dealerCode());
        ensureUniqueCode(dealerCode);
        Dealer dealer = repository.saveAndFlush(new Dealer(dealerCode, request.name().trim(),
                normalizeOptional(request.phone()), normalizeOptional(request.email()),
                normalizeOptional(request.address())));
        audit("DEALER_CREATED", dealer.getId(), null, snapshot(dealer));
        return DealerResponse.from(dealer);
    }

    @Transactional
    public DealerResponse update(UUID id, DealerUpdateRequest request) {
        Dealer dealer = find(id);
        Map<String, Object> oldValues = snapshot(dealer);
        dealer.update(request.name().trim(), normalizeOptional(request.phone()),
                normalizeOptional(request.email()), normalizeOptional(request.address()));
        repository.flush();
        audit("DEALER_UPDATED", dealer.getId(), oldValues, snapshot(dealer));
        return DealerResponse.from(dealer);
    }

    @Transactional
    public DealerResponse changeStatus(UUID id, DealerStatusRequest request) {
        Dealer dealer = find(id);
        Map<String, Object> oldValues = snapshot(dealer);
        dealer.changeStatus(request.active());
        repository.flush();
        audit("DEALER_STATUS_CHANGED", dealer.getId(), oldValues, snapshot(dealer));
        return DealerResponse.from(dealer);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID dealerId) {
        return repository.findById(dealerId).map(Dealer::isActive).orElse(false);
    }

    private Dealer find(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("DEALER_NOT_FOUND", "Không tìm thấy đại lý " + id + "."));
    }

    private void ensureUniqueCode(String dealerCode) {
        if (repository.existsByDealerCodeIgnoreCase(dealerCode)) {
            throw new BusinessConflictException("DEALER_CODE_EXISTS", "Mã đại lý đã tồn tại.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Dealer dealer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("dealerCode", dealer.getDealerCode());
        values.put("name", dealer.getName());
        values.put("phone", dealer.getPhone());
        values.put("email", dealer.getEmail());
        values.put("address", dealer.getAddress());
        values.put("active", dealer.isActive());
        return values;
    }

    private void audit(String action, UUID id, Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "DEALER", id, actorProvider.currentActor(), oldValues, newValues));
    }
}
