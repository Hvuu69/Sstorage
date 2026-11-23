package com.backend.Sstorage.inventory.supplier.api.dto;

import jakarta.validation.constraints.NotNull;

public record SupplierStatusRequest(
        @NotNull(message = "Trạng thái nhà cung cấp là bắt buộc.") Boolean active
) {}
