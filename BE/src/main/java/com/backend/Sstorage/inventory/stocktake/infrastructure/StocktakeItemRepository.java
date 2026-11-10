package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.StocktakeItem;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StocktakeItemRepository
        extends JpaRepository<StocktakeItem, UUID> {

    /**
     * Lay tat ca san pham trong mot phien kiem ke.
     * Dong thoi lay Product va nguoi kiem dem.
     */
    @EntityGraph(attributePaths = {
            "product",
            "countedBy"
    })
    @Query("""
            SELECT i
            FROM StocktakeItem i
            WHERE i.stocktake.id = :stocktakeId
            ORDER BY i.product.name ASC
            """)
    List<StocktakeItem> findAllByStocktakeId(
            @Param("stocktakeId") UUID stocktakeId
    );

    /**
     * Tim mot san pham trong mot phien kiem ke.
     */
    Optional<StocktakeItem> findByStocktake_IdAndProduct_Id(
            UUID stocktakeId,
            UUID productId
    );

    /**
     * Dem tong so san pham trong phien.
     */
    long countByStocktake_Id(UUID stocktakeId);

    /**
     * Dem so san pham da kiem dem.
     */
    long countByStocktake_IdAndActualQuantityIsNotNull(
            UUID stocktakeId
    );

    /**
     * Kiem tra con san pham chua kiem dem hay khong.
     */
    boolean existsByStocktake_IdAndActualQuantityIsNull(
            UUID stocktakeId
    );

    /**
     * Khoa dong kiem ke khi nhan vien cap nhat ket qua.
     *
     * Tranh hai request dong thoi ghi de so luong.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM StocktakeItem i
            WHERE i.id = :itemId
              AND i.stocktake.id = :stocktakeId
            """)
    Optional<StocktakeItem> findByIdAndStocktakeIdForUpdate(
            @Param("itemId") UUID itemId,
            @Param("stocktakeId") UUID stocktakeId
    );

    /**
     * Kiem tra san pham da nam trong phien kiem ke chua.
     */
    boolean existsByStocktake_IdAndProduct_Id(
            UUID stocktakeId,
            UUID productId
    );

    /**
     * Xoa cac dong kiem ke cua mot phien.
     * Chi su dung khi phien con o trang thai cho phep.
     */
    void deleteAllByStocktake_Id(UUID stocktakeId);
}