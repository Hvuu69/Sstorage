
package com.backend.Sstorage.inventory.stocktake.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;

import com.backend.Sstorage.inventory.stocktake.domain.Stocktake;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeItem;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeStatus;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeRepository;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeItemRepository;

import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.ProductRepository;

import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public StocktakeService(
            StocktakeRepository stocktakeRepository,
            StocktakeItemRepository itemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            AuditPort auditPort,
            CurrentActorProvider actorProvider
    ) {
        this.stocktakeRepository = stocktakeRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    // =====================================================
    // 1. TAO PHIEN KIEM KE
    // =====================================================

    @Transactional
    public StocktakeSummary create(String note) {

        AppUser actor = currentUser();

        requireRole(actor, Role.ADMIN, Role.WAREHOUSE_MANAGER);

        List<Product> products = productRepository.findAll(
                (Specification<Product>) (root, query, cb) ->
                        cb.equal(root.get("status"), ProductStatus.ACTIVE),
                Sort.by(Sort.Direction.ASC, "id")
        );

        if (products.isEmpty()) {
            throw new BusinessConflictException(
                    "NO_ACTIVE_PRODUCTS",
                    "Khong co san pham dang hoat dong de kiem ke."
            );
        }

        OffsetDateTime snapshotAt = databaseNow();

        Stocktake stocktake = stocktakeRepository.saveAndFlush(
                new Stocktake(
                        nextStocktakeCode(),
                        actor,
                        normalizeOptional(note),
                        snapshotAt
                )
        );

        List<StocktakeItem> items = new ArrayList<>();

        // Lock balance theo thu tu product ID de han che deadlock.
        for (Product product : products) {

            long quantity = lockAndReadBalance(product.getId());

            items.add(
                    new StocktakeItem(
                            stocktake,
                            product,
                            quantity
                    )
            );
        }

        itemRepository.saveAll(items);
        itemRepository.flush();

        audit(
                "STOCKTAKE_CREATED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 2. DANH SACH PHIEN KIEM KE
    // =====================================================

    @Transactional(readOnly = true)
    public List<StocktakeSummary> list(StocktakeStatus status) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        List<Stocktake> sessions;

        if (status == null) {
            sessions = stocktakeRepository
                    .findAllByOrderByCreatedAtDesc();
        } else {
            sessions = stocktakeRepository
                    .findAllByStatusOrderByCreatedAtDesc(status);
        }

        return sessions.stream()
                .map(this::toSummary)
                .toList();
    }

    // =====================================================
    // 3. CHI TIET PHIEN KIEM KE
    // =====================================================

    @Transactional(readOnly = true)
    public StocktakeDetail getDetail(UUID stocktakeId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        Stocktake stocktake = findDetailedSession(stocktakeId);

        List<StocktakeItem> items =
                itemRepository.findAllByStocktakeId(stocktakeId);

        return new StocktakeDetail(
                toSummary(stocktake),
                items.stream()
                        .map(this::toItemSummary)
                        .toList()
        );
    }

    // =====================================================
    // 4. BAT DAU KIEM KE
    // =====================================================

    @Transactional
    public StocktakeSummary start(UUID stocktakeId) {

        AppUser actor = currentUser();

        requireRole(actor, Role.ADMIN, Role.WAREHOUSE_MANAGER);

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus() != StocktakeStatus.DRAFT) {
            throw new BusinessConflictException(
                    "STOCKTAKE_CANNOT_START",
                    "Chi phien DRAFT moi duoc bat dau."
            );
        }

        stocktake.start();

        stocktakeRepository.flush();

        audit(
                "STOCKTAKE_STARTED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 5. NHAP SO LUONG KIEM DEM
    // =====================================================

    @Transactional
    public StocktakeItemSummary recordCount(
            UUID stocktakeId,
            UUID itemId,
            long actualQuantity,
            String note
    ) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        if (actualQuantity < 0) {
            throw new InvalidRequestException(
                    "INVALID_ACTUAL_QUANTITY",
                    "So luong kiem dem khong duoc am."
            );
        }

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus() != StocktakeStatus.IN_PROGRESS) {
            throw new BusinessConflictException(
                    "STOCKTAKE_NOT_IN_PROGRESS",
                    "Phien kiem ke khong o trang thai dang kiem dem."
            );
        }

        StocktakeItem item = itemRepository
                .findByIdAndStocktakeIdForUpdate(itemId, stocktakeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "STOCKTAKE_ITEM_NOT_FOUND",
                                "Khong tim thay san pham trong phien kiem ke."
                        )
                );

        Long oldQuantity = item.getActualQuantity();

        item.recordCount(
                actualQuantity,
                actor,
                databaseNow(),
                normalizeOptional(note)
        );

        itemRepository.flush();

        Map<String, Object> newValues = new LinkedHashMap<>();

        newValues.put("productId", item.getProduct().getId());
        newValues.put("systemQuantity", item.getSystemQuantity());
        newValues.put("actualQuantity", actualQuantity);
        newValues.put("difference", item.getDifference());

        Map<String, Object> oldValues = new LinkedHashMap<>();

        oldValues.put("actualQuantity", oldQuantity);

        auditPort.record(
                new AuditEvent(
                        "STOCKTAKE_ITEM_COUNTED",
                        "STOCKTAKE_ITEM",
                        item.getId(),
                        actorProvider.currentActor(),
                        oldValues,
                        newValues
                )
        );

        return toItemSummary(item);
    }

    // =====================================================
    // 6. GUI KET QUA KIEM KE
    // =====================================================

    @Transactional
    public StocktakeSummary submit(UUID stocktakeId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus() != StocktakeStatus.IN_PROGRESS) {
            throw new BusinessConflictException(
                    "STOCKTAKE_NOT_IN_PROGRESS",
                    "Chi phien dang kiem dem moi duoc gui."
            );
        }

        if (itemRepository.countByStocktake_Id(stocktakeId) == 0) {
            throw new BusinessConflictException(
                    "EMPTY_STOCKTAKE",
                    "Phien kiem ke chua co san pham."
            );
        }

        if (itemRepository.existsByStocktake_IdAndActualQuantityIsNull(stocktakeId)) {
            throw new BusinessConflictException(
                    "STOCKTAKE_ITEMS_NOT_COMPLETED",
                    "Van con san pham chua duoc kiem dem."
            );
        }

        stocktake.submitForConfirmation();

        stocktakeRepository.flush();

        audit(
                "STOCKTAKE_SUBMITTED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 7. DUYET VA DIEU CHINH TON KHO
    // =====================================================

    @Transactional
    public StocktakeSummary approve(UUID stocktakeId) {

        AppUser approver = currentUser();

        requireRole(
                approver,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus()
                != StocktakeStatus.PENDING_CONFIRMATION) {

            throw new BusinessConflictException(
                    "STOCKTAKE_NOT_PENDING",
                    "Chi phien dang cho duyet moi duoc phe duyet."
            );
        }

        List<StocktakeItem> items =
                itemRepository.findAllByStocktakeId(stocktakeId);

        if (items.isEmpty()
                || items.stream().anyMatch(item -> !item.isCounted())) {

            throw new BusinessConflictException(
                    "STOCKTAKE_INCOMPLETE",
                    "Phien kiem ke chua hoan thanh."
            );
        }

        // Khoa tat ca balance theo cung thu tu de tranh deadlock.
        List<StocktakeItem> orderedItems = items.stream()
                .sorted(
                        Comparator.comparing(
                                item -> item.getProduct().getId()
                        )
                )
                .toList();

        Map<UUID, Long> currentQuantities = new LinkedHashMap<>();

        for (StocktakeItem item : orderedItems) {

            UUID productId = item.getProduct().getId();

            long currentQuantity = lockAndReadBalance(productId);

            currentQuantities.put(productId, currentQuantity);
        }

        // Kiem tra co giao dich phat sinh sau snapshot hay khong.
        boolean staleSnapshot = false;

        for (StocktakeItem item : orderedItems) {

            UUID productId = item.getProduct().getId();

            boolean movementExists = hasMovementAfterSnapshot(
                    productId,
                    stocktake.getSnapshotAt()
            );

            boolean quantityChanged =
                    currentQuantities.get(productId)
                            != item.getSystemQuantity();

            if (movementExists || quantityChanged) {
                staleSnapshot = true;
                break;
            }
        }

        // Snapshot khong con hop le:
        // khong duoc ghi de ton kho hien tai.
        if (staleSnapshot) {

            stocktake.requireRecount();

            stocktakeRepository.flush();

            audit(
                    "STOCKTAKE_RECOUNT_REQUIRED",
                    stocktake,
                    null
            );

            return toSummary(stocktake);
        }

        // Cap nhat balance va ghi so giao dich trong cung transaction.
        for (StocktakeItem item : orderedItems) {

            long difference = item.getDifference();

            if (difference == 0) {
                continue;
            }

            UUID productId = item.getProduct().getId();

            Long actualQuantity = item.getActualQuantity();

            int updated = jdbcTemplate.update(
                    """
                    UPDATE public.inventory_balances
                    SET quantity_on_hand = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE product_id = ?
                    """,
                    actualQuantity,
                    productId
            );

            if (updated != 1) {
                throw new BusinessConflictException(
                        "INVENTORY_BALANCE_UPDATE_FAILED",
                        "Khong the cap nhat ton kho san pham " + productId
                );
            }

            // Chi tao ledger khi co chenh lech khac 0.
            jdbcTemplate.update(
                    """
                    INSERT INTO public.inventory_transactions
                    (
                        product_id,
                        stocktake_item_id,
                        quantity_delta
                    )
                    VALUES (?, ?, ?)
                    """,
                    productId,
                    item.getId(),
                    difference
            );
        }

        stocktake.complete(
                approver,
                databaseNow()
        );

        stocktakeRepository.flush();

        audit(
                "STOCKTAKE_APPROVED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 8. TAO LAI SNAPSHOT KHI CAN KIEM KE LAI
    // =====================================================

    @Transactional
    public StocktakeSummary refreshRecount(UUID stocktakeId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus()
                != StocktakeStatus.RECOUNT_REQUIRED) {

            throw new BusinessConflictException(
                    "STOCKTAKE_RECOUNT_NOT_REQUIRED",
                    "Phien hien tai khong yeu cau kiem ke lai."
            );
        }

        List<StocktakeItem> oldItems =
                itemRepository.findAllByStocktakeId(stocktakeId);

        if (oldItems.isEmpty()) {
            throw new BusinessConflictException(
                    "EMPTY_STOCKTAKE",
                    "Khong co san pham de kiem ke lai."
            );
        }

        List<StocktakeItem> orderedItems = oldItems.stream()
                .sorted(
                        Comparator.comparing(
                                item -> item.getProduct().getId()
                        )
                )
                .toList();

        OffsetDateTime newSnapshotAt = databaseNow();

        // Xoa cac dong cu va tao dong snapshot moi.
        // Phien cu duoc giu lai, khong xoa phien.
        itemRepository.deleteAllByStocktake_Id(stocktakeId);
        itemRepository.flush();

        List<StocktakeItem> newItems = new ArrayList<>();

        for (StocktakeItem oldItem : orderedItems) {

            Product product = oldItem.getProduct();

            long quantity = lockAndReadBalance(product.getId());

            newItems.add(
                    new StocktakeItem(
                            stocktake,
                            product,
                            quantity
                    )
            );
        }

        itemRepository.saveAll(newItems);
        itemRepository.flush();

        stocktake.refreshSnapshot(newSnapshotAt);

        stocktakeRepository.flush();

        audit(
                "STOCKTAKE_RECOUNT_STARTED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 9. HUY PHIEN KIEM KE
    // =====================================================

    @Transactional
    public StocktakeSummary cancel(UUID stocktakeId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        Stocktake stocktake = findSessionForUpdate(stocktakeId);

        if (stocktake.getStatus() == StocktakeStatus.COMPLETED
                || stocktake.getStatus() == StocktakeStatus.CANCELLED) {

            throw new BusinessConflictException(
                    "STOCKTAKE_CANNOT_CANCEL",
                    "Khong the huy phien da hoan tat hoac da huy."
            );
        }

        stocktake.cancel();

        stocktakeRepository.flush();

        audit(
                "STOCKTAKE_CANCELLED",
                stocktake,
                null
        );

        return toSummary(stocktake);
    }

    // =====================================================
    // 10. DATABASE HELPERS
    // =====================================================

    private OffsetDateTime databaseNow() {

        return jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                (rs, rowNum) ->
                        rs.getObject(1, OffsetDateTime.class)
        );
    }

    /**
     * Khoa dong ton kho de tranh cap nhat dong thoi.
     */
    private long lockAndReadBalance(UUID productId) {

        try {

            Long quantity = jdbcTemplate.queryForObject(
                    """
                    SELECT quantity_on_hand
                    FROM public.inventory_balances
                    WHERE product_id = ?
                    FOR UPDATE
                    """,
                    Long.class,
                    productId
            );

            if (quantity == null || quantity < 0) {
                throw new BusinessConflictException(
                        "INVALID_INVENTORY_BALANCE",
                        "So luong ton kho khong hop le."
                );
            }

            return quantity;

        } catch (EmptyResultDataAccessException exception) {

            throw new ResourceNotFoundException(
                    "INVENTORY_BALANCE_NOT_FOUND",
                    "Khong tim thay so ton kho cua san pham " + productId
            );
        }
    }

    /**
     * Kiem tra ledger co giao dich sau snapshot.
     */
    private boolean hasMovementAfterSnapshot(
            UUID productId,
            OffsetDateTime snapshotAt
    ) {

        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.inventory_transactions
                    WHERE product_id = ?
                      AND created_at > ?
                )
                """,
                Boolean.class,
                productId,
                snapshotAt
        );

        return Boolean.TRUE.equals(exists);
    }

    // =====================================================
    // 11. USER VA PHAN QUYEN
    // =====================================================

    private AppUser currentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new InvalidRequestException(
                    "AUTHENTICATED_USER_UNAVAILABLE",
                    "Khong xac dinh duoc nguoi dung hien tai."
            );
        }

        if (authentication.getPrincipal() instanceof AppUser principal
                && principal.getId() != null) {

            return userRepository.findById(principal.getId())
                    .orElseThrow(() ->
                            new InvalidRequestException(
                                    "AUTHENTICATED_USER_UNAVAILABLE",
                                    "Khong tim thay tai khoan hien tai."
                            )
                    );
        }

        return userRepository.findByEmailIgnoreCase(
                authentication.getName()
        ).orElseThrow(() ->
                new InvalidRequestException(
                        "AUTHENTICATED_USER_UNAVAILABLE",
                        "Khong tim thay tai khoan hien tai."
                )
        );
    }

    private void requireRole(AppUser user, Role... allowedRoles) {

        Role actualRole = user.getRole().getCode();

        for (Role role : allowedRoles) {

            if (actualRole == role) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Ban khong co quyen thuc hien chuc nang nay."
        );
    }

    // =====================================================
    // 12. REPOSITORY HELPERS
    // =====================================================

    private Stocktake findSessionForUpdate(UUID id) {

        return stocktakeRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        notFound(id)
                );
    }

    private Stocktake findDetailedSession(UUID id) {

        return stocktakeRepository.findDetailedById(id)
                .orElseThrow(() ->
                        notFound(id)
                );
    }

    private ResourceNotFoundException notFound(UUID id) {

        return new ResourceNotFoundException(
                "STOCKTAKE_NOT_FOUND",
                "Khong tim thay phien kiem ke " + id
        );
    }

    // =====================================================
    // 13. RESPONSE MAPPING
    // =====================================================

    private StocktakeSummary toSummary(Stocktake stocktake) {

        return new StocktakeSummary(
                stocktake.getId(),
                stocktake.getStocktakeCode(),
                stocktake.getStatus().name(),
                stocktake.getSnapshotAt(),
                stocktake.getNote(),
                stocktake.getCreatedBy().getId(),
                stocktake.getApprovedBy() == null
                        ? null
                        : stocktake.getApprovedBy().getId(),
                stocktake.getApprovedAt(),
                stocktake.getCreatedAt(),
                stocktake.getUpdatedAt()
        );
    }

    private StocktakeItemSummary toItemSummary(StocktakeItem item) {

        Product product = item.getProduct();

        return new StocktakeItemSummary(
                item.getId(),
                product.getId(),
                product.getSku(),
                product.getName(),
                item.getSystemQuantity(),
                item.getActualQuantity(),
                item.getDifference(),
                item.getCountedBy() == null
                        ? null
                        : item.getCountedBy().getId(),
                item.getCountedAt(),
                item.getNote()
        );
    }

    // =====================================================
    // 14. AUDIT
    // =====================================================

    private void audit(
            String action,
            Stocktake stocktake,
            Map<String, Object> oldValues
    ) {

        Map<String, Object> newValues = new LinkedHashMap<>();

        newValues.put("stocktakeCode", stocktake.getStocktakeCode());
        newValues.put("status", stocktake.getStatus().name());
        newValues.put("snapshotAt", stocktake.getSnapshotAt());

        auditPort.record(
                new AuditEvent(
                        action,
                        "STOCKTAKE",
                        stocktake.getId(),
                        actorProvider.currentActor(),
                        oldValues,
                        newValues
                )
        );
    }

    private String nextStocktakeCode() {

        return "STK-"
                + System.currentTimeMillis()
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
    }

    private String normalizeOptional(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    // =====================================================
    // 15. RESPONSE RECORDS
    // =====================================================

    public record StocktakeSummary(
            UUID id,
            String stocktakeCode,
            String status,
            OffsetDateTime snapshotAt,
            String note,
            UUID createdBy,
            UUID approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record StocktakeItemSummary(
            UUID id,
            UUID productId,
            String sku,
            String productName,
            long systemQuantity,
            Long actualQuantity,
            Long difference,
            UUID countedBy,
            OffsetDateTime countedAt,
            String note
    ) {
    }

    public record StocktakeDetail(
            StocktakeSummary stocktake,
            List<StocktakeItemSummary> items
    ) {
    }
}
