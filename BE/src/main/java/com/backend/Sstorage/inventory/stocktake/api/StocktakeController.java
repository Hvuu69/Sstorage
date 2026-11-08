
package com.backend.Sstorage.inventory.stocktake.api;

import com.backend.Sstorage.inventory.stocktake.application.StocktakeService;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeStatus;
import com.backend.Sstorage.inventory.stocktake.application.StocktakeScanService;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/stocktakes")
public class StocktakeController {

    private final StocktakeService service;
    private final StocktakeScanService scanService;

    public StocktakeController(
            StocktakeService service,
            StocktakeScanService scanService
    ) {
        this.service = service;
        this.scanService = scanService;
    }

    // =====================================================
    // 1. TAO PHIEN KIEM KE
    // POST /api/v1/stocktakes
    // =====================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<StocktakeService.StocktakeSummary> create(
            @Valid @RequestBody CreateStocktakeRequest request
    ) {

        StocktakeService.StocktakeSummary response =
                service.create(request.note());

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/stocktakes/" + response.id()
                ))
                .body(response);
    }

    // =====================================================
    // 2. DANH SACH PHIEN KIEM KE
    // GET /api/v1/stocktakes
    // =====================================================

    @GetMapping
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public List<StocktakeService.StocktakeSummary> list(
            @RequestParam(required = false) StocktakeStatus status
    ) {
        return service.list(status);
    }

    // =====================================================
    // 3. CHI TIET PHIEN KIEM KE
    // GET /api/v1/stocktakes/{id}
    // =====================================================

    @GetMapping("/{id}")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public StocktakeService.StocktakeDetail getDetail(
            @PathVariable UUID id
    ) {
        return service.getDetail(id);
    }

    // =====================================================
    // 4. BAT DAU KIEM KE
    // POST /api/v1/stocktakes/{id}/start
    // =====================================================

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public StocktakeService.StocktakeSummary start(
            @PathVariable UUID id
    ) {
        return service.start(id);
    }

    // =====================================================
    // 5. NHAP SO LUONG KIEM DEM
    // PATCH /api/v1/stocktakes/{id}/items/{itemId}/count
    // =====================================================

    @PatchMapping("/{id}/items/{itemId}/count")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public StocktakeService.StocktakeItemSummary recordCount(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody RecordCountRequest request
    ) {

        return service.recordCount(
                id,
                itemId,
                request.actualQuantity(),
                request.note()
        );
    }

    // =====================================================
    // 6. GUI KET QUA KIEM KE
    // POST /api/v1/stocktakes/{id}/submit
    // =====================================================

    @PostMapping("/{id}/submit")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public StocktakeService.StocktakeSummary submit(
            @PathVariable UUID id
    ) {
        return service.submit(id);
    }

    // =====================================================
    // 7. QUAN LY PHE DUYET
    // POST /api/v1/stocktakes/{id}/approve
    // =====================================================

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public StocktakeService.StocktakeSummary approve(
            @PathVariable UUID id
    ) {
        return service.approve(id);
    }

    // =====================================================
    // 8. YEU CAU KIEM KE LAI
    // POST /api/v1/stocktakes/{id}/recount
    // =====================================================

    @PostMapping("/{id}/recount")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public StocktakeService.StocktakeSummary recount(
            @PathVariable UUID id
    ) {
        return service.refreshRecount(id);
    }

    // =====================================================
    // 9. HUY PHIEN KIEM KE
    // POST /api/v1/stocktakes/{id}/cancel
    // =====================================================

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public StocktakeService.StocktakeSummary cancel(
            @PathVariable UUID id
    ) {
        return service.cancel(id);
    }

    // =====================================================
    // 10. QUET QR/RFID TRONG PHIEN KIEM KE
    // POST /api/v1/stocktakes/{id}/items/{itemId}/scan
    // =====================================================

    @PostMapping("/{id}/items/{itemId}/scan")
    @PreAuthorize("""
        hasAnyRole(
            'ADMIN',
            'WAREHOUSE_MANAGER',
            'WAREHOUSE_STAFF'
        )
        """)
    public StocktakeScanService.ScanResult scan(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody ScanRequest request
    ) {
        return scanService.scan(
                id,
                itemId,
                request.code()
        );
    }

    public record ScanRequest(

            @NotBlank(message = "Ma QR/RFID la bat buoc.")
            @Size(max = 255, message = "Ma QR/RFID toi da 255 ky tu.")
            String code

    ) {
    }

    // =====================================================
    // REQUEST DTO
    // =====================================================

    public record CreateStocktakeRequest(

            @Size(
                    max = 2000,
                    message = "Ghi chu khong duoc vuot qua 2000 ky tu."
            )
            String note

    ) {
    }

    public record RecordCountRequest(

            @NotNull(message = "So luong kiem dem la bat buoc.")
            @PositiveOrZero(message = "So luong khong duoc am.")
            Long actualQuantity,

            @Size(
                    max = 2000,
                    message = "Ghi chu khong duoc vuot qua 2000 ky tu."
            )
            String note

    ) {
    }
}
