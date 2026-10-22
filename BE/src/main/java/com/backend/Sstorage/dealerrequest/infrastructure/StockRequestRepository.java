package com.backend.Sstorage.dealerrequest.infrastructure;

import com.backend.Sstorage.dealerrequest.domain.StockRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockRequestRepository extends JpaRepository<StockRequest, UUID>,
        JpaSpecificationExecutor<StockRequest> {

    @EntityGraph(attributePaths = {"dealer", "requestedBy", "reviewedBy", "items", "items.product",
            "items.product.category", "items.product.unit"})
    @Query("select request from StockRequest request where request.id = :id")
    Optional<StockRequest> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"dealer", "requestedBy", "reviewedBy", "items", "items.product",
            "items.product.category", "items.product.unit"})
    @Query("select request from StockRequest request where request.id = :id and request.dealer.id = :dealerId")
    Optional<StockRequest> findDetailedByIdAndDealerId(@Param("id") UUID id, @Param("dealerId") UUID dealerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StockRequest request where request.id = :id")
    Optional<StockRequest> findByIdForUpdate(@Param("id") UUID id);
}
