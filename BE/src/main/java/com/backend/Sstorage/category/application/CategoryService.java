package com.backend.Sstorage.category.application;

import com.backend.Sstorage.category.api.dto.CategoryCreateRequest;
import com.backend.Sstorage.category.api.dto.CategoryResponse;
import com.backend.Sstorage.category.api.dto.CategoryStatusRequest;
import com.backend.Sstorage.category.api.dto.CategoryUpdateRequest;
import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.common.text.BusinessCodeNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository repository;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public CategoryService(CategoryRepository repository, AuditPort auditPort, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(String search, Boolean active, boolean activeOnly, Pageable pageable) {
        Specification<Category> specification = (root, query, builder) -> builder.conjunction();
        Specification<Category> searchFilter = searchSpecification(search);
        Specification<Category> activeFilter = activeSpecification(activeOnly ? Boolean.TRUE : active);
        if (searchFilter != null) specification = specification.and(searchFilter);
        if (activeFilter != null) specification = specification.and(activeFilter);
        return repository.findAll(specification, pageable).map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id, boolean activeOnly) {
        Category category = find(id);
        if (activeOnly && !category.isActive()) {
            throw notFound(id);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String code = BusinessCodeNormalizer.normalize(request.code());
        ensureUniqueCode(code, null);
        Category category = repository.save(new Category(code, request.name().trim(), normalizeOptional(request.description())));
        audit("CATEGORY_CREATED", category.getId(), null, snapshot(category));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        Category category = find(id);
        Map<String, Object> oldValues = snapshot(category);
        String code = BusinessCodeNormalizer.normalize(request.code());
        ensureUniqueCode(code, id);
        category.update(code, request.name().trim(), normalizeOptional(request.description()));
        audit("CATEGORY_UPDATED", category.getId(), oldValues, snapshot(category));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse changeStatus(UUID id, CategoryStatusRequest request) {
        Category category = find(id);
        Map<String, Object> oldValues = snapshot(category);
        category.changeStatus(request.active());
        audit("CATEGORY_STATUS_CHANGED", category.getId(), oldValues, snapshot(category));
        return CategoryResponse.from(category);
    }

    private Category find(UUID id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục " + id + ".");
    }

    private void ensureUniqueCode(String code, UUID currentId) {
        boolean exists = currentId == null
                ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (exists) {
            throw new BusinessConflictException("CATEGORY_CODE_EXISTS", "Mã danh mục đã tồn tại.");
        }
    }

    private Specification<Category> searchSpecification(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("code")), pattern),
                builder.like(builder.lower(root.get("name")), pattern));
    }

    private Specification<Category> activeSpecification(Boolean active) {
        return active == null ? null : (root, query, builder) -> builder.equal(root.get("active"), active);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Category category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", category.getCode());
        values.put("name", category.getName());
        values.put("description", category.getDescription());
        values.put("active", category.isActive());
        return values;
    }

    private void audit(String action, UUID id, Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "CATEGORY", id, actorProvider.currentActor(), oldValues, newValues));
    }
}
