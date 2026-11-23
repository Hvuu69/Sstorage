package com.backend.Sstorage.inventory.api.infrastructure;

import com.backend.Sstorage.inventory.api.domain.InventoryVoucher;
import com.backend.Sstorage.inventory.api.domain.VoucherType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryVoucherRepository extends JpaRepository<InventoryVoucher, UUID> {
    // Thêm dòng này để hỗ trợ lấy danh sách theo loại (Nhập/Xuất)
    Page<InventoryVoucher> findByType(VoucherType type, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"lines", "lines.product", "lines.product.unit"})
    @Query("select v from InventoryVoucher v where v.id = :id")
    Optional<InventoryVoucher> findByIdForUpdate(@Param("id") UUID id);
}
