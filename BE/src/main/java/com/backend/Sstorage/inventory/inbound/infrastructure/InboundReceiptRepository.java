package com.backend.Sstorage.inventory.inbound.infrastructure;

import com.backend.Sstorage.inventory.inbound.domain.InboundReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
    @EntityGraph(attributePaths = {"supplier", "createdBy", "confirmedBy", "items", "items.product", "items.product.unit"})
    Page<InboundReceipt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "createdBy", "confirmedBy", "items", "items.product", "items.product.unit"})
    @Query("select r from InboundReceipt r where r.id = :id")
    Optional<InboundReceipt> findDetail(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"supplier", "createdBy", "confirmedBy", "items", "items.product", "items.product.unit"})
    @Query("select r from InboundReceipt r where r.id = :id")
    Optional<InboundReceipt> findForUpdate(@Param("id") UUID id);
}
