package com.backend.Sstorage.inventory.inbound.infrastructure;

import com.backend.Sstorage.inventory.inbound.domain.SupplierProduct;
import com.backend.Sstorage.inventory.inbound.domain.SupplierProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, SupplierProductId> {
    @EntityGraph(attributePaths = {"supplier"})
    @Query("""
            select sp from SupplierProduct sp
            where sp.product.id in :productIds
            order by sp.supplier.name
            """)
    List<SupplierProduct> findAllByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    @EntityGraph(attributePaths = {"supplier"})
    @Query("""
            select sp from SupplierProduct sp
            where sp.product.id = :productId
            order by sp.supplier.name
            """)
    List<SupplierProduct> findAllByProductId(@Param("productId") UUID productId);

    @EntityGraph(attributePaths = {"product", "product.category", "product.unit"})
    @Query("""
            select sp from SupplierProduct sp
            where sp.supplier.id = :supplierId
              and sp.supplier.active = true
              and sp.active = true
              and sp.product.status = com.backend.Sstorage.product.domain.ProductStatus.ACTIVE
            """)
    Page<SupplierProduct> findSelectable(@Param("supplierId") UUID supplierId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category", "product.unit"})
    @Query("""
            select sp from SupplierProduct sp
            where sp.supplier.id = :supplierId
              and sp.supplier.active = true
              and sp.active = true
              and sp.product.status = com.backend.Sstorage.product.domain.ProductStatus.ACTIVE
              and (lower(sp.product.sku) like :searchPattern
                   or lower(sp.product.name) like :searchPattern)
            """)
    Page<SupplierProduct> searchSelectable(@Param("supplierId") UUID supplierId,
                                           @Param("searchPattern") String searchPattern,
                                           Pageable pageable);

    @Query("""
            select count(sp) > 0 from SupplierProduct sp
            where sp.supplier.id = :supplierId and sp.product.id = :productId
              and sp.supplier.active = true and sp.active = true
              and sp.product.status = com.backend.Sstorage.product.domain.ProductStatus.ACTIVE
            """)
    boolean isSelectable(@Param("supplierId") UUID supplierId, @Param("productId") UUID productId);
}
