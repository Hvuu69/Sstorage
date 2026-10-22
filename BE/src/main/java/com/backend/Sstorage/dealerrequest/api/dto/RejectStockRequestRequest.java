package com.backend.Sstorage.dealerrequest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectStockRequestRequest(
        @NotBlank(message = "Lý do từ chối không được để trống.")
        @Size(max = 2000, message = "Lý do từ chối không được vượt quá 2.000 ký tự.")
        String reason
) {
}
