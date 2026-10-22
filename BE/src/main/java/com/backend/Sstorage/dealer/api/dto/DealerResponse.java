package com.backend.Sstorage.dealer.api.dto;

import com.backend.Sstorage.dealer.domain.Dealer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DealerResponse(
        UUID id,
        String dealerCode,
        String name,
        String phone,
        String email,
        String address,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DealerResponse from(Dealer dealer) {
        return new DealerResponse(dealer.getId(), dealer.getDealerCode(), dealer.getName(), dealer.getPhone(),
                dealer.getEmail(), dealer.getAddress(), dealer.isActive(), dealer.getCreatedAt(),
                dealer.getUpdatedAt());
    }
}
