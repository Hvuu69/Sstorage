package com.backend.Sstorage.inventory.api.dto;

import java.util.UUID;

public record VoucherLineRequest(
        UUID productId,
        int quantity
) {}