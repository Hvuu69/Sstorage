package com.backend.Sstorage.dealer.api.dto;

import jakarta.validation.constraints.NotNull;

public record DealerStatusRequest(
        @NotNull(message = "Trạng thái đại lý là bắt buộc.") Boolean active
) {
}
