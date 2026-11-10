
package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, UUID>,
        InventoryTransactionRepositoryCustom {

    boolean existsByProduct_IdInAndCreatedAtAfter(
            Collection<UUID> productIds,
            OffsetDateTime timestamp
    );

    boolean existsByStocktakeItem_Id(UUID stocktakeItemId);
}
