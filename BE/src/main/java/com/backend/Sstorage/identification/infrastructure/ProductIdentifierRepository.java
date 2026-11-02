package com.backend.Sstorage.identification.infrastructure;

import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductIdentifierRepository extends JpaRepository<ProductIdentifier, UUID> {
    @EntityGraph(attributePaths = {"product", "product.category", "product.unit"})
    List<ProductIdentifier> findAllByValue(String value);

    @EntityGraph(attributePaths = {"product"})
    List<ProductIdentifier> findAllByProductIdOrderByCreatedAtDesc(UUID productId);

    boolean existsByValue(String value);

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductIdentifier> findFirstByProductIdAndTypeAndActiveTrueOrderByCreatedAtDesc(
            UUID productId, IdentifierType type);
}
