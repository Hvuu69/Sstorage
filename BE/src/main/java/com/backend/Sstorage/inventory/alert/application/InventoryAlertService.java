
package com.backend.Sstorage.inventory.alert.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;

import com.backend.Sstorage.inventory.alert.domain.InventoryAlert;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertStatus;
import com.backend.Sstorage.inventory.alert.domain.InventoryAlertType;
import com.backend.Sstorage.inventory.alert.domain.InventoryPolicy;

import com.backend.Sstorage.inventory.alert.infrastructure.InventoryAlertRepository;
import com.backend.Sstorage.inventory.alert.infrastructure.InventoryPolicyRepository;

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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryAlertService {

    private static final Set<InventoryAlertStatus> ACTIVE_STATUSES =
            Set.of(
                    InventoryAlertStatus.NEW,
                    InventoryAlertStatus.ACKNOWLEDGED
            );

    private final InventoryAlertRepository alertRepository;
    private final InventoryPolicyRepository policyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public InventoryAlertService(
            InventoryAlertRepository alertRepository,
            InventoryPolicyRepository policyRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            AuditPort auditPort,
            CurrentActorProvider actorProvider
    ) {
        this.alertRepository = alertRepository;
        this.policyRepository = policyRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    // =====================================================
    // 1. DANH SACH CANH BAO
    // =====================================================

    @Transactional(readOnly = true)
    public List<AlertSummary> list(
            InventoryAlertStatus status,
            InventoryAlertType alertType
    ) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        List<InventoryAlert> alerts =
                alertRepository.findAllByOrderByCreatedAtDesc();

        return alerts.stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> alertType == null || a.getAlertType() == alertType)
                .map(this::toSummary)
                .toList();
    }

    // =====================================================
    // 2. CHI TIET CANH BAO
    // =====================================================

    @Transactional(readOnly = true)
    public AlertSummary getDetail(UUID alertId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        InventoryAlert alert = alertRepository
                .findDetailedById(alertId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "INVENTORY_ALERT_NOT_FOUND",
                                "Khong tim thay canh bao ton kho."
                        )
                );

        return toSummary(alert);
    }

    // =====================================================
    // 3. DANH SACH CHINH SACH TON KHO
    // =====================================================

    @Transactional(readOnly = true)
    public List<PolicySummary> listPolicies() {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        return policyRepository
                .findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toPolicySummary)
                .toList();
    }

    // =====================================================
    // 4. CAP NHAT CHINH SACH TON KHO
    // =====================================================

    @Transactional
    public PolicySummary updatePolicy(
            UUID productId,
            PolicyRequest request
    ) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        validatePolicy(request);

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "PRODUCT_NOT_FOUND",
                                "Khong tim thay san pham."
                        )
                );

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "PRODUCT_INACTIVE",
                    "Khong the cau hinh chinh sach cho san pham ngung hoat dong."
            );
        }

        InventoryPolicy policy =
                policyRepository.findByProductIdForUpdate(productId)
                        .orElse(null);

        Map<String, Object> oldValues = new LinkedHashMap<>();

        if (policy == null) {

            policy = new InventoryPolicy(
                    product,
                    request.minimumQuantity(),
                    request.maximumQuantity(),
                    request.safetyStock(),
                    request.leadTimeDays(),
                    actor
            );

        } else {

            oldValues = policySnapshot(policy);

            policy.update(
                    request.minimumQuantity(),
                    request.maximumQuantity(),
                    request.safetyStock(),
                    request.leadTimeDays(),
                    actor
            );
        }

        InventoryPolicy saved =
                policyRepository.saveAndFlush(policy);

        audit(
                "INVENTORY_POLICY_UPDATED",
                "INVENTORY_POLICY",
                productId,
                oldValues,
                policySnapshot(saved)
        );

        return toPolicySummary(saved);
    }

    // =====================================================
    // 5. DANH GIA TON KHO MOT SAN PHAM
    // =====================================================

    @Transactional
    public EvaluationResult evaluateProduct(UUID productId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        return evaluateProductInternal(productId);
    }

    // =====================================================
    // 6. DANH GIA TOAN BO CHINH SACH TON KHO
    // =====================================================

    @Transactional
    public List<EvaluationResult> evaluateAll() {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        List<InventoryPolicy> policies =
                policyRepository.findAllByOrderByUpdatedAtDesc();

        // Khoa theo thu tu product ID de han che deadlock.
        List<UUID> productIds = policies.stream()
                .map(InventoryPolicy::getProductId)
                .sorted(Comparator.naturalOrder())
                .toList();

        List<EvaluationResult> results = new ArrayList<>();

        for (UUID productId : productIds) {
            results.add(evaluateProductInternal(productId));
        }

        return results;
    }

    // =====================================================
    // 7. NGHIEP VU DANH GIA VA TAO CANH BAO
    // =====================================================

    private EvaluationResult evaluateProductInternal(
            UUID productId
    ) {

        InventoryPolicy policy =
                policyRepository.findByProductIdForUpdate(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "INVENTORY_POLICY_NOT_FOUND",
                                        "San pham chua co chinh sach ton kho."
                                )
                        );

        Product product = policy.getProduct();

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessConflictException(
                    "PRODUCT_INACTIVE",
                    "Khong danh gia canh bao cho san pham ngung hoat dong."
            );
        }

        // Doc ton kho hien tai va khoa dong du lieu.
        long currentQuantity = lockAndReadBalance(productId);

        InventoryAlertType alertType;
        Long suggestedQuantity;
        String message;

        if (policy.isLowStock(currentQuantity)) {

            alertType = InventoryAlertType.LOW_STOCK;

            long targetQuantity = Math.max(
                    policy.getMinimumQuantity(),
                    policy.getSafetyStock()
            );

            suggestedQuantity =
                    targetQuantity - currentQuantity;

            message = String.format(
                    "San pham %s dang thieu hang. Ton hien tai: %d, muc toi thieu: %d, ton an toan: %d.",
                    product.getSku(),
                    currentQuantity,
                    policy.getMinimumQuantity(),
                    policy.getSafetyStock()
            );

        } else if (policy.isOverstock(currentQuantity)) {

            alertType = InventoryAlertType.OVERSTOCK;

            suggestedQuantity =
                    currentQuantity - policy.getMaximumQuantity();

            message = String.format(
                    "San pham %s dang vuot muc ton toi da. Ton hien tai: %d, muc toi da: %d, so luong vuot muc: %d.",
                    product.getSku(),
                    currentQuantity,
                    policy.getMaximumQuantity(),
                    suggestedQuantity
            );

        } else {

            return new EvaluationResult(
                    productId,
                    currentQuantity,
                    "NORMAL",
                    false,
                    null
            );
        }

        // Kiem tra canh bao dang mo.
        var existingAlert = alertRepository
                .findFirstByProduct_IdAndAlertTypeAndStatusInOrderByCreatedAtDesc(
                        productId,
                        alertType,
                        ACTIVE_STATUSES
                );

        if (existingAlert.isPresent()) {

            return new EvaluationResult(
                    productId,
                    currentQuantity,
                    "ALREADY_ALERTED",
                    false,
                    toSummary(existingAlert.get())
            );
        }

        // Tao canh bao moi.
        InventoryAlert alert = new InventoryAlert(
                product,
                null,
                alertType,
                currentQuantity,
                suggestedQuantity,
                message
        );

        InventoryAlert saved =
                alertRepository.saveAndFlush(alert);

        audit(
                "INVENTORY_ALERT_CREATED",
                "INVENTORY_ALERT",
                saved.getId(),
                null,
                alertSnapshot(saved)
        );

        return new EvaluationResult(
                productId,
                currentQuantity,
                alertType.name(),
                true,
                toSummary(saved)
        );
    }

    // =====================================================
    // 8. QUAN LY TIEP NHAN CANH BAO
    // =====================================================

    @Transactional
    public AlertSummary acknowledge(UUID alertId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        InventoryAlert alert = alertRepository
                .findByIdForUpdate(alertId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "INVENTORY_ALERT_NOT_FOUND",
                                "Khong tim thay canh bao."
                        )
                );

        Map<String, Object> oldValues = alertSnapshot(alert);

        alert.acknowledge(
                actor,
                databaseNow()
        );

        alertRepository.flush();

        audit(
                "INVENTORY_ALERT_ACKNOWLEDGED",
                "INVENTORY_ALERT",
                alert.getId(),
                oldValues,
                alertSnapshot(alert)
        );

        return toSummary(alert);
    }

    // =====================================================
    // 9. QUAN LY DONG CANH BAO
    // =====================================================

    @Transactional
    public AlertSummary resolve(UUID alertId) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER
        );

        InventoryAlert alert = alertRepository
                .findByIdForUpdate(alertId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "INVENTORY_ALERT_NOT_FOUND",
                                "Khong tim thay canh bao."
                        )
                );

        Map<String, Object> oldValues = alertSnapshot(alert);

        alert.resolve(databaseNow());

        alertRepository.flush();

        audit(
                "INVENTORY_ALERT_RESOLVED",
                "INVENTORY_ALERT",
                alert.getId(),
                oldValues,
                alertSnapshot(alert)
        );

        return toSummary(alert);
    }

    // =====================================================
    // 10. DATABASE
    // =====================================================

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
                    "Khong tim thay so ton kho cua san pham."
            );
        }
    }

    private OffsetDateTime databaseNow() {

        return jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                (rs, rowNum) ->
                        rs.getObject(1, OffsetDateTime.class)
        );
    }

    // =====================================================
    // 11. VALIDATION
    // =====================================================

    private void validatePolicy(PolicyRequest request) {

        if (request == null) {
            throw new InvalidRequestException(
                    "POLICY_REQUIRED",
                    "Thong tin chinh sach khong duoc de trong."
            );
        }

        if (request.minimumQuantity() < 0) {
            throw new InvalidRequestException(
                    "INVALID_MINIMUM_QUANTITY",
                    "Ton toi thieu khong duoc am."
            );
        }

        if (request.maximumQuantity() != null
                && request.maximumQuantity() < request.minimumQuantity()) {

            throw new InvalidRequestException(
                    "INVALID_MAXIMUM_QUANTITY",
                    "Ton toi da phai lon hon hoac bang ton toi thieu."
            );
        }

        if (request.safetyStock() < 0) {
            throw new InvalidRequestException(
                    "INVALID_SAFETY_STOCK",
                    "Ton an toan khong duoc am."
            );
        }

        if (request.leadTimeDays() < 0) {
            throw new InvalidRequestException(
                    "INVALID_LEAD_TIME",
                    "Thoi gian cung ung khong duoc am."
            );
        }
    }

    // =====================================================
    // 12. AUTHENTICATION VA AUTHORIZATION
    // =====================================================

    private AppUser currentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Yeu cau dang nhap de thuc hien chuc nang nay."
            );
        }

        if (authentication.getPrincipal() instanceof AppUser principal) {

            return userRepository.findById(principal.getId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "USER_NOT_FOUND",
                                    "Khong tim thay tai khoan."
                            )
                    );
        }

        return userRepository.findByEmailIgnoreCase(
                authentication.getName()
        ).orElseThrow(() ->
                new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "Khong tim thay tai khoan."
                )
        );
    }

    private void requireRole(
            AppUser user,
            Role... allowedRoles
    ) {

        Role actualRole = user.getRole().getCode();

        for (Role allowed : allowedRoles) {
            if (actualRole == allowed) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Ban khong co quyen thuc hien chuc nang nay."
        );
    }

    // =====================================================
    // 13. AUDIT
    // =====================================================

    private void audit(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    ) {

        auditPort.record(
                new AuditEvent(
                        action,
                        entityType,
                        entityId,
                        actorProvider.currentActor(),
                        oldValues,
                        newValues
                )
        );
    }

    private Map<String, Object> policySnapshot(
            InventoryPolicy policy
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("productId", policy.getProductId());
        values.put("minimumQuantity", policy.getMinimumQuantity());
        values.put("maximumQuantity", policy.getMaximumQuantity());
        values.put("safetyStock", policy.getSafetyStock());
        values.put("leadTimeDays", policy.getLeadTimeDays());

        return values;
    }

    private Map<String, Object> alertSnapshot(
            InventoryAlert alert
    ) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("productId", alert.getProduct().getId());
        values.put("alertType", alert.getAlertType().name());
        values.put("status", alert.getStatus().name());
        values.put("currentQuantity", alert.getCurrentQuantity());
        values.put("suggestedQuantity", alert.getSuggestedQuantity());

        return values;
    }

    // =====================================================
    // 14. RESPONSE MAPPING
    // =====================================================

    private AlertSummary toSummary(InventoryAlert alert) {

        return new AlertSummary(
                alert.getId(),
                alert.getProduct().getId(),
                alert.getProduct().getSku(),
                alert.getProduct().getName(),
                alert.getForecastId(),
                alert.getAlertType().name(),
                alert.getStatus().name(),
                alert.getCurrentQuantity(),
                alert.getSuggestedQuantity(),
                alert.getMessage(),
                alert.getAcknowledgedBy() == null
                        ? null
                        : alert.getAcknowledgedBy().getId(),
                alert.getAcknowledgedAt(),
                alert.getResolvedAt(),
                alert.getCreatedAt()
        );
    }

    private PolicySummary toPolicySummary(
            InventoryPolicy policy
    ) {

        return new PolicySummary(
                policy.getProductId(),
                policy.getProduct().getSku(),
                policy.getProduct().getName(),
                policy.getMinimumQuantity(),
                policy.getMaximumQuantity(),
                policy.getSafetyStock(),
                policy.getLeadTimeDays(),
                policy.getUpdatedBy() == null
                        ? null
                        : policy.getUpdatedBy().getId(),
                policy.getUpdatedAt()
        );
    }

    // =====================================================
    // 15. RESPONSE RECORDS
    // =====================================================

    public record PolicyRequest(
            long minimumQuantity,
            Long maximumQuantity,
            long safetyStock,
            int leadTimeDays
    ) {
    }

    public record PolicySummary(
            UUID productId,
            String sku,
            String productName,
            long minimumQuantity,
            Long maximumQuantity,
            long safetyStock,
            int leadTimeDays,
            UUID updatedBy,
            OffsetDateTime updatedAt
    ) {
    }

    public record AlertSummary(
            UUID id,
            UUID productId,
            String sku,
            String productName,
            UUID forecastId,
            String alertType,
            String status,
            long currentQuantity,
            Long suggestedQuantity,
            String message,
            UUID acknowledgedBy,
            OffsetDateTime acknowledgedAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record EvaluationResult(
            UUID productId,
            long currentQuantity,
            String evaluationStatus,
            boolean created,
            AlertSummary alert
    ) {
    }
}
