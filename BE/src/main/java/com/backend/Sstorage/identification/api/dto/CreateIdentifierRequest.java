package com.backend.Sstorage.identification.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIdentifierRequest(
        @NotNull(message = "Loại mã là bắt buộc.")
        IdentifierRegistrationType type,

        @Size(max = 255, message = "Giá trị mã không được vượt quá 255 ký tự.")
        String value
) {
}
