package com.backend.Sstorage.category.api.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryStatusRequest(
        @NotNull(message = "Trạng thái danh mục là bắt buộc.") Boolean active
) {
}
