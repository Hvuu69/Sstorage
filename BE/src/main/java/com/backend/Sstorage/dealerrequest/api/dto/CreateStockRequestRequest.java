package com.backend.Sstorage.dealerrequest.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStockRequestRequest(
        @NotNull(message = "Danh sách sản phẩm là bắt buộc.")
        @Size(min = 1, max = 100, message = "Yêu cầu phải có từ 1 đến 100 sản phẩm.")
        List<@Valid CreateStockRequestItem> items,
        @Size(max = 2000, message = "Ghi chú không được vượt quá 2.000 ký tự.") String note
) {
}
