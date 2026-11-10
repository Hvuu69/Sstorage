package com.backend.Sstorage.inventory.api.dto;

import java.util.List;

public record CreateVoucherRequest(
        String partnerName,       // Nhận từ Frontend
        String note,
        String requestCode,
        List<VoucherLineRequest> lines // Nhận từ Frontend
) {}