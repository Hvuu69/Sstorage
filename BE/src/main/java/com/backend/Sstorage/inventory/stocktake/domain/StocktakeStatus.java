package com.backend.Sstorage.inventory.stocktake.domain;

public enum StocktakeStatus {

    // Phiên mới được tạo
    DRAFT,

    // Đang tiến hành kiểm đếm
    IN_PROGRESS,

    // Nhân viên đã gửi kết quả, chờ quản lý duyệt
    PENDING_CONFIRMATION,

    // Có biến động tồn kho, cần kiểm kê lại
    RECOUNT_REQUIRED,

    // Đã hoàn tất và được quản lý phê duyệt
    COMPLETED,

    // Phiên đã bị hủy
    CANCELLED
}