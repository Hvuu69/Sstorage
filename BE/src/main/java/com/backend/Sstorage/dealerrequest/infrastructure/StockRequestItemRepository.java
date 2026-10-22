package com.backend.Sstorage.dealerrequest.infrastructure;

import com.backend.Sstorage.dealerrequest.domain.StockRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockRequestItemRepository extends JpaRepository<StockRequestItem, UUID> {
}
