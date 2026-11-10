package com.backend.Sstorage.inventory.api.infrastructure;

import com.backend.Sstorage.inventory.api.domain.InventoryVoucher;
import com.backend.Sstorage.inventory.api.domain.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryVoucherRepository extends JpaRepository<InventoryVoucher, UUID> {
    // Thêm dòng này để hỗ trợ lấy danh sách theo loại (Nhập/Xuất)
    Page<InventoryVoucher> findByType(VoucherType type, Pageable pageable);
}