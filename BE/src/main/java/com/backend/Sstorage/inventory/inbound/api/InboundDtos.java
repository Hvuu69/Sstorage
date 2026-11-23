package com.backend.Sstorage.inventory.inbound.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class InboundDtos {
    private InboundDtos() {}

    public record LineRequest(@NotNull UUID productId, @Positive long quantity) {}
    public record SaveRequest(
            @NotNull UUID supplierId,
            @Size(max = 2000) String note,
            @NotEmpty @Size(max = 100) List<@Valid LineRequest> lines
    ) {}
    public record ScanRequest(@NotBlank @Size(max = 255) String code) {}
    public record ItemResponse(UUID id, UUID productId, String sku, String name, String unit, long quantity) {}
    public record ReceiptResponse(
            UUID id, String code, UUID supplierId, String supplierCode, String partner,
            String status, String note, String createdByName, String confirmedByName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime confirmedAt,
            List<ItemResponse> items, long totalQuantity
    ) {}
    public record SupplierSummary(UUID id, String code, String name) {}
    public record SupplierProductResponse(
            UUID productId, String sku, String productName, String category,
            String unit, String identifierType, String identifierValue
    ) {}
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
