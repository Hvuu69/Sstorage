package com.backend.Sstorage.product.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.product.api.dto.ProductCreateRequest;
import com.backend.Sstorage.product.api.dto.ProductInternalResponse;
import com.backend.Sstorage.product.api.dto.ProductStatusRequest;
import com.backend.Sstorage.product.api.dto.ProductUpdateRequest;
import com.backend.Sstorage.product.application.ProductService;
import com.backend.Sstorage.product.domain.ProductStatus;
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
@RequestMapping("/api/v1/products")
public class ProductController {
    private static final Set<String> ALLOWED_SORTS = Set.of("sku", "name", "status", "createdAt", "updatedAt");
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public PageResponse<?> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            Authentication authentication) {
        var pageable = PageRequestFactory.create(page, size, sort, ALLOWED_SORTS);
        return isDealer(authentication)
                ? PageResponse.from(service.listCatalog(search, categoryId, pageable))
                : PageResponse.from(service.listInternal(search, categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public Object get(@PathVariable UUID id, Authentication authentication) {
        return isDealer(authentication) ? service.getCatalog(id) : service.getInternal(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<ProductInternalResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductInternalResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductInternalResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody ProductUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductInternalResponse changeStatus(@PathVariable UUID id,
                                                @Valid @RequestBody ProductStatusRequest request) {
        return service.changeStatus(id, request);
    }

    private boolean isDealer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DEALER"));
    }
}
