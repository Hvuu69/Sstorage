package com.backend.Sstorage.unit.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.unit.api.dto.UnitCreateRequest;
import com.backend.Sstorage.unit.api.dto.UnitResponse;
import com.backend.Sstorage.unit.api.dto.UnitUpdateRequest;
import com.backend.Sstorage.unit.application.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/units")
public class UnitController {
    private static final Set<String> ALLOWED_SORTS = Set.of("code", "name", "symbol", "createdAt");
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public PageResponse<UnitResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return PageResponse.from(service.list(search,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF','DEALER')")
    public UnitResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody UnitCreateRequest request) {
        UnitResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/units/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public UnitResponse update(@PathVariable UUID id, @Valid @RequestBody UnitUpdateRequest request) {
        return service.update(id, request);
    }
}
