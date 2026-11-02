package com.backend.Sstorage.identification.api.dto;

import java.util.UUID;

public record QrCodeResponse(
        UUID productId,
        UUID identifierId,
        String identifierValue,
        String suggestedFileName
) {
}
