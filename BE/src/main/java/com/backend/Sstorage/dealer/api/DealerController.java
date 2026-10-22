package com.backend.Sstorage.dealer.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.dealer.api.dto.DealerCreateRequest;
import com.backend.Sstorage.dealer.api.dto.DealerResponse;
import com.backend.Sstorage.dealer.api.dto.DealerStatusRequest;
import com.backend.Sstorage.dealer.api.dto.DealerUpdateRequest;
import com.backend.Sstorage.dealer.application.DealerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/dealers")
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
public class DealerController {
    private static final Set<String> ALLOWED_SORTS =
            Set.of("dealerCode", "name", "active", "createdAt", "updatedAt");
    private final DealerService service;

    public DealerController(DealerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<DealerResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return PageResponse.from(service.list(search, active,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    public DealerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<DealerResponse> create(@Valid @RequestBody DealerCreateRequest request) {
        DealerResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/dealers/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public DealerResponse update(@PathVariable UUID id, @Valid @RequestBody DealerUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public DealerResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody DealerStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
