
package com.backend.Sstorage.inventory.alert.infrastructure;

import com.backend.Sstorage.inventory.alert.domain.InventoryPolicy;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryPolicyRepository
        extends JpaRepository<InventoryPolicy, UUID> {

    /**
     * Tim chinh sach ton kho theo san pham.
     */
    @EntityGraph(attributePaths = {
            "product",
            "updatedBy"
    })
    Optional<InventoryPolicy> findByProductId(UUID productId);

    /**
     * Kiem tra san pham da co chinh sach ton kho chua.
     */
    boolean existsByProductId(UUID productId);

    /**
     * Lay tat ca chinh sach ton kho.
     */
    @EntityGraph(attributePaths = {
            "product",
            "updatedBy"
    })
    List<InventoryPolicy> findAllByOrderByUpdatedAtDesc();

    /**
     * Tim chinh sach va khoa dong trong transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM InventoryPolicy p
            WHERE p.productId = :productId
            """)
    Optional<InventoryPolicy> findByProductIdForUpdate(
            @Param("productId") UUID productId
    );

    /**
     * Tim chinh sach theo nguoi cap nhat.
     */
    List<InventoryPolicy> findAllByUpdatedBy_Id(
            UUID userId
    );
}
