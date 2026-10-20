package com.backend.Sstorage.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductUpdateRequest(
        @NotBlank(message = "SKU không được để trống.")
        @Size(max = 60, message = "SKU không được vượt quá 60 ký tự.")
        String sku,
        @NotBlank(message = "Tên sản phẩm không được để trống.")
        @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
        String name,
        @NotNull(message = "Danh mục không được để trống.") UUID categoryId,
        @NotNull(message = "Đơn vị tính không được để trống.") UUID unitId,
        String description
) {
}
