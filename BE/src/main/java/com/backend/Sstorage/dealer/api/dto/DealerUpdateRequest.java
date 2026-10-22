package com.backend.Sstorage.dealer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DealerUpdateRequest(
        @NotBlank(message = "Tên đại lý không được để trống.")
        @Size(max = 200, message = "Tên đại lý không được vượt quá 200 ký tự.")
        String name,
        @NotBlank(message = "Số điện thoại không được để trống.")
        @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự.")
        @Pattern(regexp = "^\\s*(?:\\+84|0)(?:[ .-]?\\d){9}\\s*$", message = "Số điện thoại Việt Nam không đúng định dạng.")
        String phone,
        @Size(max = 254, message = "Email không được vượt quá 254 ký tự.")
        @Email(message = "Email không đúng định dạng.")
        String email,
        @NotBlank(message = "Địa chỉ không được để trống.")
        String address
) {
}
