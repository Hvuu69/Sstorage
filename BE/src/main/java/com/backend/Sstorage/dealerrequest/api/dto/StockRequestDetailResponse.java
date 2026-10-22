package com.backend.Sstorage.dealerrequest.api.dto;

import com.backend.Sstorage.dealerrequest.domain.StockRequest;
import com.backend.Sstorage.dealerrequest.domain.StockRequestStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StockRequestDetailResponse(
        UUID id,
        String requestCode,
        UUID dealerId,
        String dealerCode,
        String dealerName,
        UUID requestedBy,
        String requestedByName,
        StockRequestStatus status,
        String note,
        String reviewNote,
        UUID reviewedBy,
        String reviewedByName,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<StockRequestItemResponse> items
) {
    public static StockRequestDetailResponse from(StockRequest request) {
        return new StockRequestDetailResponse(request.getId(), request.getRequestCode(),
                request.getDealer().getId(), request.getDealer().getDealerCode(), request.getDealer().getName(),
                request.getRequestedBy().getId(), request.getRequestedBy().getFullName(), request.getStatus(),
                request.getNote(), request.getReviewNote(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName(),
                request.getReviewedAt(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getItems().stream().map(StockRequestItemResponse::from).toList());
    }
}
