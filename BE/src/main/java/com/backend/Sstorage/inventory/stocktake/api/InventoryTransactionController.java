package com.backend.Sstorage.inventory.stocktake.api;

import com.backend.Sstorage.common.web.PageRequestFactory;
import com.backend.Sstorage.common.web.PageResponse;
import com.backend.Sstorage.inventory.stocktake.api.dto.InventoryTransactionResponse;
import com.backend.Sstorage.inventory.stocktake.application.InventoryTransactionQueryService;
import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransactionSource;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/transactions")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER','WAREHOUSE_STAFF')")
public class InventoryTransactionController {

    private static final Set<String> ALLOWED_SORTS =
            Set.of("createdAt", "quantityDelta");

    private final InventoryTransactionQueryService service;

    public InventoryTransactionController(
            InventoryTransactionQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryTransactionResponse> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) InventoryTransactionSource source,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        Pageable pageable = PageRequestFactory.create(
                page,
                size,
                sort,
                ALLOWED_SORTS
        );

        return PageResponse.from(
                service.list(
                        productId,
                        search,
                        source,
                        from,
                        to,
                        pageable
                )
        );
    }
}