package com.backend.Sstorage.product.infrastructure;

import com.backend.Sstorage.product.domain.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {
}
