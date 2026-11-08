
package com.backend.Sstorage.inventory.alert.api;

import com.backend.Sstorage.inventory.alert.application.InventoryAlertService;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertStatus;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class InventoryAlertController {

    private final InventoryAlertService service;

    public InventoryAlertController(
            InventoryAlertService service
    ) {
        this.service = service;
    }

    // =====================================================
    // 1. DANH SACH CANH BAO
    // GET /api/v1/alerts
    // =====================================================

    @GetMapping
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public List<InventoryAlertService.AlertSummary> list(
            @RequestParam(required = false)
            InventoryAlertStatus status,

            @RequestParam(required = false)
            InventoryAlertType alertType
    ) {
        return service.list(status, alertType);
    }

    // =====================================================
    // 2. CHI TIET CANH BAO
    // GET /api/v1/alerts/{id}
    // =====================================================

    @GetMapping("/{id}")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public InventoryAlertService.AlertSummary getDetail(
            @PathVariable UUID id
    ) {
        return service.getDetail(id);
    }

    // =====================================================
    // 3. DANH SACH CHINH SACH TON KHO
    // GET /api/v1/alerts/policies
    // =====================================================

    @GetMapping("/policies")
    @PreAuthorize("""
            hasAnyRole(
                'ADMIN',
                'WAREHOUSE_MANAGER',
                'WAREHOUSE_STAFF'
            )
            """)
    public List<InventoryAlertService.PolicySummary> listPolicies() {
        return service.listPolicies();
    }

    // =====================================================
    // 4. CAP NHAT CHINH SACH TON KHO
    // PUT /api/v1/alerts/policies/{productId}
    // =====================================================

    @PutMapping("/policies/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public InventoryAlertService.PolicySummary updatePolicy(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdatePolicyRequest request
    ) {

        InventoryAlertService.PolicyRequest policyRequest =
                new InventoryAlertService.PolicyRequest(
                        request.minimumQuantity(),
                        request.maximumQuantity(),
                        request.safetyStock(),
                        request.leadTimeDays()
                );

        return service.updatePolicy(
                productId,
                policyRequest
        );
    }

    // =====================================================
    // 5. DANH GIA TON KHO MOT SAN PHAM
    // POST /api/v1/alerts/evaluate/{productId}
    // =====================================================

    @PostMapping("/evaluate/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public InventoryAlertService.EvaluationResult evaluateProduct(
            @PathVariable UUID productId
    ) {
        return service.evaluateProduct(productId);
    }

    // =====================================================
    // 6. DANH GIA TOAN BO TON KHO
    // POST /api/v1/alerts/evaluate
    // =====================================================

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public List<InventoryAlertService.EvaluationResult> evaluateAll() {
        return service.evaluateAll();
    }

    // =====================================================
    // 7. TIEP NHAN CANH BAO
    // PATCH /api/v1/alerts/{id}/acknowledge
    // =====================================================

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public InventoryAlertService.AlertSummary acknowledge(
            @PathVariable UUID id
    ) {
        return service.acknowledge(id);
    }

    // =====================================================
    // 8. XAC NHAN DA XU LY CANH BAO
    // PATCH /api/v1/alerts/{id}/resolve
    // =====================================================

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public InventoryAlertService.AlertSummary resolve(
            @PathVariable UUID id
    ) {
        return service.resolve(id);
    }

    // =====================================================
    // REQUEST DTO
    // =====================================================

    public record UpdatePolicyRequest(

            @NotNull(message = "Ton kho toi thieu la bat buoc.")
            @PositiveOrZero(message = "Ton kho toi thieu khong duoc am.")
            Long minimumQuantity,

            @PositiveOrZero(message = "Ton kho toi da khong duoc am.")
            Long maximumQuantity,

            @NotNull(message = "Ton kho an toan la bat buoc.")
            @PositiveOrZero(message = "Ton kho an toan khong duoc am.")
            Long safetyStock,

            @NotNull(message = "Thoi gian cung ung la bat buoc.")
            @PositiveOrZero(message = "Thoi gian cung ung khong duoc am.")
            Integer leadTimeDays

    ) {
    }
}
