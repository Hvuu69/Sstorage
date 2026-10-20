package com.backend.Sstorage.category.api;

import com.backend.Sstorage.category.api.dto.CategoryCreateRequest;
import com.backend.Sstorage.category.api.dto.CategoryResponse;
import com.backend.Sstorage.category.api.dto.CategoryStatusRequest;
import com.backend.Sstorage.category.api.dto.CategoryUpdateRequest;
import com.backend.Sstorage.category.application.CategoryService;
import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private static final Set<String> ALLOWED_SORTS = Set.of("code", "name", "active", "createdAt", "updatedAt");
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public PageResponse<CategoryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            Authentication authentication) {
        return PageResponse.from(service.list(search, active, isDealer(authentication),
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public CategoryResponse get(@PathVariable UUID id, Authentication authentication) {
        return service.get(id, isDealer(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public CategoryResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody CategoryStatusRequest request) {
        return service.changeStatus(id, request);
    }

    private boolean isDealer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DEALER"));
    }
}
