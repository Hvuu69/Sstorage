package com.backend.Sstorage.inventory.api.infrastructure;

import com.backend.Sstorage.inventory.api.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {
    boolean existsBySupplierCodeIgnoreCase(String supplierCode);
    List<Supplier> findAllByOrderByNameAsc();
    Page<Supplier> findByActiveTrue(Pageable pageable);

    @Query("""
            select s from Supplier s
            where s.active = true
              and (lower(s.supplierCode) like :searchPattern
                   or lower(s.name) like :searchPattern)
            """)
    Page<Supplier> searchActive(@Param("searchPattern") String searchPattern, Pageable pageable);
}
