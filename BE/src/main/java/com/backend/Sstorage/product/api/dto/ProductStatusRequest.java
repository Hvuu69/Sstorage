package com.backend.Sstorage.product.api.dto;

import com.backend.Sstorage.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
        @NotNull(message = "Trạng thái sản phẩm không được để trống.") ProductStatus status
) {
}
