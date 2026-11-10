
package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InventoryTransactionRepositoryCustom {

    Page<InventoryTransaction> search(
            UUID productId,
            String search,
            String source,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable
    );
}
