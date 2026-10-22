package com.backend.Sstorage.dealerrequest.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.dealerrequest.api.dto.CreateStockRequestRequest;
import com.backend.Sstorage.dealerrequest.api.dto.RejectStockRequestRequest;
import com.backend.Sstorage.dealerrequest.api.dto.StockRequestDetailResponse;
import com.backend.Sstorage.dealerrequest.api.dto.StockRequestSummaryResponse;
import com.backend.Sstorage.dealerrequest.application.DealerRequestService;
import com.backend.Sstorage.dealerrequest.domain.StockRequestStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dealer-requests")
public class DealerRequestController {
    private static final Set<String> ALLOWED_SORTS =
            Set.of("requestCode", "status", "createdAt", "updatedAt", "reviewedAt");
    private final DealerRequestService service;

    public DealerRequestController(DealerRequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('DEALER')")
    public ResponseEntity<StockRequestDetailResponse> create(
            @Valid @RequestBody CreateStockRequestRequest request) {
        StockRequestDetailResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/dealer-requests/my/" + response.id())).body(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('DEALER')")
    public PageResponse<StockRequestSummaryResponse> listMine(
            @RequestParam(required = false) StockRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return PageResponse.from(service.listMine(status,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/my/{id}")
    @PreAuthorize("hasRole('DEALER')")
    public StockRequestDetailResponse getMine(@PathVariable UUID id) {
        return service.getMine(id);
    }

    @GetMapping("/approved")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    public PageResponse<StockRequestDetailResponse> listApproved(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,asc") String sort) {
        return PageResponse.from(service.listApproved(
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PageResponse<StockRequestSummaryResponse> listAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID dealerId,
            @RequestParam(required = false) StockRequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return PageResponse.from(service.listAll(search, dealerId, status, from, to,
                PageRequestFactory.create(page, size, sort, ALLOWED_SORTS)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public StockRequestDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public StockRequestDetailResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public StockRequestDetailResponse reject(@PathVariable UUID id,
                                             @Valid @RequestBody RejectStockRequestRequest request) {
        return service.reject(id, request);
    }
}
