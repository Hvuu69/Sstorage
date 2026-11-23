package com.backend.Sstorage.product.infrastructure;

import com.backend.Sstorage.product.domain.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.productId = :productId")
    Optional<InventoryBalance> findByProductIdForUpdate(@Param("productId") UUID productId);
}
