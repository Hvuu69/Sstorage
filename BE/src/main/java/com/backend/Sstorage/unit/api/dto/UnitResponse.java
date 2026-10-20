package com.backend.Sstorage.unit.api.dto;

import com.backend.Sstorage.unit.domain.Unit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnitResponse(UUID id, String code, String name, String symbol, OffsetDateTime createdAt) {
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName(), unit.getSymbol(), unit.getCreatedAt());
    }
}
