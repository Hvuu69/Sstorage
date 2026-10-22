package com.backend.Sstorage.dealer.infrastructure;

import com.backend.Sstorage.dealer.domain.Dealer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DealerRepository extends JpaRepository<Dealer, UUID>, JpaSpecificationExecutor<Dealer> {
    boolean existsByDealerCodeIgnoreCase(String dealerCode);
}
