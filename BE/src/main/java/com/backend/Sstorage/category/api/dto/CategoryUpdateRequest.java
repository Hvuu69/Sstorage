package com.backend.Sstorage.category.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "Mã danh mục không được để trống.")
        @Size(max = 30, message = "Mã danh mục không được vượt quá 30 ký tự.")
        String code,
        @NotBlank(message = "Tên danh mục không được để trống.")
        @Size(max = 150, message = "Tên danh mục không được vượt quá 150 ký tự.")
        String name,
        String description
) {
}
