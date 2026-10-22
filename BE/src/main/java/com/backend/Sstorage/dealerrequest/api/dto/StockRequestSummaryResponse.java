package com.backend.Sstorage.dealerrequest.api.dto;

import com.backend.Sstorage.dealerrequest.domain.StockRequest;
import com.backend.Sstorage.dealerrequest.domain.StockRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockRequestSummaryResponse(
        UUID id,
        String requestCode,
        UUID dealerId,
        String dealerCode,
        String dealerName,
        StockRequestStatus status,
        int totalLines,
        long totalQuantity,
        String reviewNote,
        String reviewedByName,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StockRequestSummaryResponse from(StockRequest request) {
        return new StockRequestSummaryResponse(request.getId(), request.getRequestCode(),
                request.getDealer().getId(), request.getDealer().getDealerCode(), request.getDealer().getName(),
                request.getStatus(), request.getItems().size(),
                request.getItems().stream().mapToLong(item -> item.getQuantity()).sum(), request.getReviewNote(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName(),
                request.getReviewedAt(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
