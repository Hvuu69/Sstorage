package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, UUID> {

    @EntityGraph(attributePaths = {
            "product.unit",
            "stocktakeItem.stocktake"
    })
    @Query(
            value = """
                    SELECT t
                    FROM InventoryTransaction t
                    WHERE (:productId IS NULL
                           OR t.product.id = :productId)
                    AND (
                        :search IS NULL
                        OR LOWER(t.product.sku) LIKE CONCAT('%', :search, '%')
                        OR LOWER(t.product.name) LIKE CONCAT('%', :search, '%')
                    )
                    AND (
                        :source IS NULL
                        OR (:source = 'STOCKTAKE'
                            AND t.stocktakeItem IS NOT NULL)
                        OR (:source = 'INBOUND'
                            AND t.inboundItemId IS NOT NULL)
                        OR (:source = 'OUTBOUND'
                            AND t.outboundItemId IS NOT NULL)
                    )
                    AND (:fromDate IS NULL
                         OR t.createdAt >= :fromDate)
                    AND (:toDate IS NULL
                         OR t.createdAt <= :toDate)
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM InventoryTransaction t
                    WHERE (:productId IS NULL
                           OR t.product.id = :productId)
                    AND (
                        :search IS NULL
                        OR LOWER(t.product.sku) LIKE CONCAT('%', :search, '%')
                        OR LOWER(t.product.name) LIKE CONCAT('%', :search, '%')
                    )
                    AND (
                        :source IS NULL
                        OR (:source = 'STOCKTAKE'
                            AND t.stocktakeItem IS NOT NULL)
                        OR (:source = 'INBOUND'
                            AND t.inboundItemId IS NOT NULL)
                        OR (:source = 'OUTBOUND'
                            AND t.outboundItemId IS NOT NULL)
                    )
                    AND (:fromDate IS NULL
                         OR t.createdAt >= :fromDate)
                    AND (:toDate IS NULL
                         OR t.createdAt <= :toDate)
                    """
    )
    Page<InventoryTransaction> search(
            @Param("productId") UUID productId,
            @Param("search") String search,
            @Param("source") String source,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable
    );

    boolean existsByProduct_IdInAndCreatedAtAfter(
            Collection<UUID> productIds,
            OffsetDateTime timestamp
    );

    boolean existsByStocktakeItem_Id(UUID stocktakeItemId);
}