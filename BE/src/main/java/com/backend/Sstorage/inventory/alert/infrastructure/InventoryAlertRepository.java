
package com.backend.Sstorage.inventory.alert.infrastructure;

import com.backend.Sstorage.inventory.alert.domain.InventoryAlert;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertStatus;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertType;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertRepository
        extends JpaRepository<InventoryAlert, UUID> {

    /**
     * Lay toan bo canh bao, moi nhat truoc.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    List<InventoryAlert> findAllByOrderByCreatedAtDesc();

    /**
     * Lay danh sach canh bao theo trang thai.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    List<InventoryAlert> findAllByStatusOrderByCreatedAtDesc(
            InventoryAlertStatus status
    );

    /**
     * Lay danh sach canh bao theo loai.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    List<InventoryAlert> findAllByAlertTypeOrderByCreatedAtDesc(
            InventoryAlertType alertType
    );

    /**
     * Loc canh bao theo nhieu trang thai.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    List<InventoryAlert> findAllByStatusInOrderByCreatedAtDesc(
            Collection<InventoryAlertStatus> statuses
    );

    /**
     * Lay canh bao cua mot san pham.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    List<InventoryAlert> findAllByProduct_IdOrderByCreatedAtDesc(
            UUID productId
    );

    /**
     * Kiem tra san pham da co canh bao cung loai
     * dang NEW hoac ACKNOWLEDGED hay chua.
     */
    boolean existsByProduct_IdAndAlertTypeAndStatusIn(
            UUID productId,
            InventoryAlertType alertType,
            Collection<InventoryAlertStatus> statuses
    );

    /**
     * Tim canh bao dang mo gan nhat.
     */
    Optional<InventoryAlert>
    findFirstByProduct_IdAndAlertTypeAndStatusInOrderByCreatedAtDesc(
            UUID productId,
            InventoryAlertType alertType,
            Collection<InventoryAlertStatus> statuses
    );

    /**
     * Khoa canh bao khi cap nhat trang thai.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM InventoryAlert a
            WHERE a.id = :id
            """)
    Optional<InventoryAlert> findByIdForUpdate(
            @Param("id") UUID id
    );

    /**
     * Chi tiet canh bao kem thong tin san pham
     * va nguoi tiep nhan.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    @Query("""
            SELECT a
            FROM InventoryAlert a
            WHERE a.id = :id
            """)
    Optional<InventoryAlert> findDetailedById(
            @Param("id") UUID id
    );

    /**
     * Danh sach canh bao co phan trang.
     */
    @EntityGraph(attributePaths = {
            "product",
            "acknowledgedBy"
    })
    @Query(
            value = """
                    SELECT a
                    FROM InventoryAlert a
                    """,
            countQuery = """
                    SELECT COUNT(a)
                    FROM InventoryAlert a
                    """
    )
    Page<InventoryAlert> findAllAlerts(Pageable pageable);

    /**
     * Dem canh bao theo trang thai.
     */
    long countByStatus(InventoryAlertStatus status);

    /**
     * Dem canh bao cua tung san pham.
     */
    long countByProduct_Id(UUID productId);
}
