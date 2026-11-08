package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.Stocktake;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StocktakeRepository
        extends JpaRepository<Stocktake, UUID> {

    // Tim phien theo ma kiem ke
    Optional<Stocktake> findByStocktakeCode(String stocktakeCode);

    // Kiem tra ma phien da ton tai
    boolean existsByStocktakeCode(String stocktakeCode);

    // Lay danh sach phien moi nhat
    List<Stocktake> findAllByOrderByCreatedAtDesc();

    // Loc danh sach theo trang thai
    List<Stocktake> findAllByStatusOrderByCreatedAtDesc(
            StocktakeStatus status
    );

    // Lay thong tin chi tiet cua phien
    @EntityGraph(attributePaths = {
            "createdBy",
            "approvedBy"
    })
    @Query("""
            SELECT s
            FROM Stocktake s
            WHERE s.id = :id
            """)
    Optional<Stocktake> findDetailedById(
            @Param("id") UUID id
    );

    /**
     * Khoa phien kiem ke khi xu ly nghiep vu.
     *
     * Tranh hai request dong thoi cung duyet
     * hoac thay doi mot phien kiem ke.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM Stocktake s
            WHERE s.id = :id
            """)
    Optional<Stocktake> findByIdForUpdate(
            @Param("id") UUID id
    );
}