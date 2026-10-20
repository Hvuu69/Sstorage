package com.backend.Sstorage.unit.infrastructure;

import com.backend.Sstorage.unit.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID>, JpaSpecificationExecutor<Unit> {
    boolean existsByCodeIgnoreCase(String code);
}
