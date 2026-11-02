package com.backend.Sstorage.identification.api.dto;

import jakarta.validation.constraints.NotNull;

public record IdentifierStatusRequest(
        @NotNull(message = "Trạng thái mã là bắt buộc.") Boolean active
) {
}
