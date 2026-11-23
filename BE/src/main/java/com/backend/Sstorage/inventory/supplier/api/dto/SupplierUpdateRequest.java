package com.backend.Sstorage.inventory.supplier.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống.")
        @Size(max = 200, message = "Tên nhà cung cấp không được vượt quá 200 ký tự.")
        String name,
        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự.")
        String phone,
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không đúng định dạng.")
        @Size(max = 254, message = "Email không được vượt quá 254 ký tự.")
        String email,
        String address
) {}
