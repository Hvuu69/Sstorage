package com.backend.Sstorage.unit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitUpdateRequest(
        @NotBlank(message = "Tên đơn vị không được để trống.")
        @Size(max = 100, message = "Tên đơn vị không được vượt quá 100 ký tự.")
        String name,
        @NotBlank(message = "Ký hiệu đơn vị không được để trống.")
        @Size(max = 20, message = "Ký hiệu đơn vị không được vượt quá 20 ký tự.")
        String symbol
) {
}
