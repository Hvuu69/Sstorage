package com.backend.Sstorage.inventory.api.infrastructure;

import com.backend.Sstorage.inventory.api.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
}