package com.backend.Sstorage.dealerrequest.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateStockRequestItem(
        @NotNull(message = "Sản phẩm là bắt buộc.") UUID productId,
        @NotNull(message = "Số lượng là bắt buộc.")
        @Positive(message = "Số lượng phải là số nguyên dương.")
        @Max(value = 1_000_000_000L, message = "Số lượng mỗi sản phẩm không được vượt quá 1.000.000.000.")
        Long quantity
) {
}
