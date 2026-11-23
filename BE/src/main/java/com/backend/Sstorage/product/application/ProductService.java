package com.backend.Sstorage.product.application;
import com.backend.Sstorage.product.infrastructure.InventoryBalanceRepository;
import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.inventory.api.infrastructure.SupplierRepository;
import com.backend.Sstorage.inventory.inbound.domain.SupplierProduct;
import com.backend.Sstorage.inventory.inbound.infrastructure.SupplierProductRepository;
import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.common.text.BusinessCodeNormalizer;
import com.backend.Sstorage.product.api.dto.ProductCatalogResponse;
import com.backend.Sstorage.product.api.dto.ProductCreateRequest;
import com.backend.Sstorage.product.api.dto.ProductInternalResponse;
import com.backend.Sstorage.product.api.dto.ProductSupplierResponse;
import com.backend.Sstorage.product.api.dto.ProductStatusRequest;
import com.backend.Sstorage.product.api.dto.ProductUpdateRequest;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.backend.Sstorage.product.domain.InventoryBalance;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductReferencePort referencePort;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          UnitRepository unitRepository, InventoryBalanceRepository balanceRepository,
                          SupplierRepository supplierRepository, SupplierProductRepository supplierProductRepository,
                          ProductReferencePort referencePort, AuditPort auditPort,
                          CurrentActorProvider actorProvider) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.balanceRepository = balanceRepository;
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.referencePort = referencePort;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<ProductInternalResponse> listInternal(String search, UUID categoryId, UUID supplierId, ProductStatus status,
                                                      Pageable pageable) {
        Page<Product> products = productRepository.findAll(specification(search, categoryId, supplierId, status), pageable);
        Map<UUID, InventoryBalance> balances = balances(products);
        Map<UUID, List<ProductSupplierResponse>> suppliers = supplierResponses(
                products.getContent().stream().map(Product::getId).toList());
        return products.map(product -> ProductInternalResponse.from(product,
                quantity(balances.get(product.getId())), suppliers.getOrDefault(product.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public Page<ProductCatalogResponse> listCatalog(String search, UUID categoryId, Pageable pageable) {
        return productRepository.findAll(specification(search, categoryId, null, ProductStatus.ACTIVE), pageable)
                .map(ProductCatalogResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductInternalResponse getInternal(UUID id) {
        Product product = findProduct(id);
        Long quantity = balanceRepository.findById(id).map(InventoryBalance::getQuantityOnHand).orElse(null);
        return response(product, quantity);
    }

    @Transactional(readOnly = true)
    public ProductCatalogResponse getCatalog(UUID id) {
        Product product = findProduct(id);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw notFound(id);
        }
        return ProductCatalogResponse.from(product);
    }

    @Transactional
    public ProductInternalResponse create(ProductCreateRequest request) {
        String sku = BusinessCodeNormalizer.normalize(request.sku());
        ensureUniqueSku(sku, null);
        Category category = findActiveCategory(request.categoryId());
        Unit unit = findUnit(request.unitId());
        Product product = productRepository.saveAndFlush(new Product(category, unit, sku, request.name().trim(),
                normalizeOptional(request.description())));
        balanceRepository.save(new InventoryBalance(product));
        syncSuppliers(product, request.supplierIds());
        audit("PRODUCT_CREATED", product.getId(), null, snapshot(product));
        return response(product, 0L);
    }

    @Transactional
    public ProductInternalResponse update(UUID id, ProductUpdateRequest request) {
        Product product = findProduct(id);
        Map<String, Object> oldValues = snapshot(product);
        String sku = BusinessCodeNormalizer.normalize(request.sku());
        if (!sku.equals(product.getSku())) {
            if (referencePort.hasBusinessReferences(id)) {
                throw new BusinessConflictException("PRODUCT_SKU_IMMUTABLE",
                        "Không thể đổi SKU vì sản phẩm đã có dữ liệu nghiệp vụ liên quan.");
            }
            ensureUniqueSku(sku, id);
        }

        Category category = product.getCategory().getId().equals(request.categoryId())
                ? product.getCategory() : findActiveCategory(request.categoryId());
        Unit unit = product.getUnit().getId().equals(request.unitId())
                ? product.getUnit() : findUnit(request.unitId());
        product.update(category, unit, sku, request.name().trim(), normalizeOptional(request.description()));
        if (request.supplierIds() != null) syncSuppliers(product, request.supplierIds());
        productRepository.flush();
        audit("PRODUCT_UPDATED", product.getId(), oldValues, snapshot(product));
        Long quantity = balanceRepository.findById(id).map(InventoryBalance::getQuantityOnHand).orElse(null);
        return response(product, quantity);
    }

    @Transactional
    public ProductInternalResponse changeStatus(UUID id, ProductStatusRequest request) {
        Product product = findProduct(id);
        Map<String, Object> oldValues = snapshot(product);
        product.changeStatus(request.status());
        productRepository.flush();
        audit("PRODUCT_STATUS_CHANGED", product.getId(), oldValues, snapshot(product));
        Long quantity = balanceRepository.findById(id).map(InventoryBalance::getQuantityOnHand).orElse(null);
        return response(product, quantity);
    }

    private Specification<Product> specification(String search, UUID categoryId, UUID supplierId, ProductStatus status) {
        Specification<Product> result = (root, query, builder) -> builder.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            result = result.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("sku")), pattern),
                    builder.like(builder.lower(root.get("name")), pattern)));
        }
        if (categoryId != null) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("category").get("id"), categoryId));
        }
        if (supplierId != null) {
            result = result.and((root, query, builder) -> {
                var links = query.subquery(UUID.class);
                var link = links.from(SupplierProduct.class);
                links.select(link.get("product").get("id"))
                        .where(builder.equal(link.get("supplier").get("id"), supplierId));
                return root.get("id").in(links);
            });
        }
        if (status != null) {
            result = result.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        return result;
    }

    private Map<UUID, InventoryBalance> balances(Page<Product> products) {
        return balanceRepository.findAllById(products.getContent().stream().map(Product::getId).toList()).stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, Function.identity()));
    }

    private Long quantity(InventoryBalance balance) {
        return balance == null ? null : balance.getQuantityOnHand();
    }

    private ProductInternalResponse response(Product product, Long quantity) {
        return ProductInternalResponse.from(product, quantity,
                supplierProductRepository.findAllByProductId(product.getId()).stream()
                        .map(ProductSupplierResponse::from).toList());
    }

    private Map<UUID, List<ProductSupplierResponse>> supplierResponses(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<UUID, List<ProductSupplierResponse>> result = new HashMap<>();
        for (SupplierProduct link : supplierProductRepository.findAllByProductIdIn(productIds)) {
            result.computeIfAbsent(link.getProduct().getId(), ignored -> new ArrayList<>())
                    .add(ProductSupplierResponse.from(link));
        }
        return result;
    }

    private void syncSuppliers(Product product, Set<UUID> requestedSupplierIds) {
        Set<UUID> desired = requestedSupplierIds == null ? Set.of() : new HashSet<>(requestedSupplierIds);
        List<SupplierProduct> existing = supplierProductRepository.findAllByProductId(product.getId());
        Map<UUID, SupplierProduct> linksBySupplier = existing.stream().collect(Collectors.toMap(
                link -> link.getSupplier().getId(), Function.identity()));
        Map<UUID, Supplier> suppliers = supplierRepository.findAllById(desired).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));

        for (UUID supplierId : desired) {
            Supplier supplier = suppliers.get(supplierId);
            if (supplier == null) {
                throw new ResourceNotFoundException("SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp " + supplierId + ".");
            }
            SupplierProduct link = linksBySupplier.get(supplierId);
            boolean activatesLink = link == null || !link.isActive();
            if (activatesLink && !supplier.isActive()) {
                throw new InvalidRequestException("SUPPLIER_INACTIVE", "Không thể gán nhà cung cấp đã ngừng hoạt động.");
            }
            if (link == null) supplierProductRepository.save(new SupplierProduct(supplier, product));
            else if (!link.isActive()) link.changeStatus(true);
        }
        existing.stream().filter(SupplierProduct::isActive)
                .filter(link -> !desired.contains(link.getSupplier().getId()))
                .forEach(link -> link.changeStatus(false));
        supplierProductRepository.flush();
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private Category findActiveCategory(UUID id) {
        Category category = categoryRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục " + id + "."));
        if (!category.isActive()) {
            throw new InvalidRequestException("CATEGORY_INACTIVE", "Danh mục đã ngừng hoạt động.");
        }
        return category;
    }

    private Unit findUnit(UUID id) {
        return unitRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("UNIT_NOT_FOUND", "Không tìm thấy đơn vị " + id + "."));
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm " + id + ".");
    }

    private void ensureUniqueSku(String sku, UUID currentId) {
        boolean exists = currentId == null
                ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, currentId);
        if (exists) {
            throw new BusinessConflictException("PRODUCT_SKU_EXISTS", "SKU đã tồn tại.");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sku", product.getSku());
        values.put("name", product.getName());
        values.put("description", product.getDescription());
        values.put("categoryId", product.getCategory().getId());
        values.put("unitId", product.getUnit().getId());
        values.put("status", product.getStatus());
        values.put("supplierIds", supplierProductRepository.findAllByProductId(product.getId()).stream()
                .filter(SupplierProduct::isActive)
                .map(link -> link.getSupplier().getId().toString())
                .sorted()
                .toList());
        return values;
    }

    private void audit(String action, UUID id, Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "PRODUCT", id, actorProvider.currentActor(), oldValues, newValues));
    }
}
