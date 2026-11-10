
package com.backend.Sstorage.inventory.stocktake.application;

import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.inventory.stocktake.api.dto.InventoryTransactionResponse;
import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransactionSource;
import com.backend.Sstorage.inventory.stocktake.infrastructure.InventoryTransactionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryTransactionQueryService {

    private final InventoryTransactionRepository repository;

    public InventoryTransactionQueryService(
            InventoryTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> list(
            UUID productId,
            String search,
            InventoryTransactionSource source,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    ) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "INVALID_DATE_RANGE",
                    "Thời điểm bắt đầu phải nhỏ hơn hoặc bằng thời điểm kết thúc."
            );
        }

        String normalizedSearch =
                search == null || search.isBlank()
                        ? null
                        : search.trim().toLowerCase(Locale.ROOT);

        String sourceName =
                source == null ? null : source.name();

        return repository.search(
                productId,
                normalizedSearch,
                sourceName,
                from,
                to,
                pageable
        ).map(InventoryTransactionResponse::from);
    }
}
