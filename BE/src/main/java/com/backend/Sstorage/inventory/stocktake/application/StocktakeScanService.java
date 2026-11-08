
package com.backend.Sstorage.inventory.stocktake.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;

import com.backend.Sstorage.identification.api.dto.IdentifierLookupResponse;
import com.backend.Sstorage.identification.application.IdentificationService;

import com.backend.Sstorage.inventory.stocktake.domain.Stocktake;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeItem;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeStatus;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeRepository;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeItemRepository;

import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StocktakeScanService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository itemRepository;
    private final IdentificationService identificationService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public StocktakeScanService(
            StocktakeRepository stocktakeRepository,
            StocktakeItemRepository itemRepository,
            IdentificationService identificationService,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            AuditPort auditPort,
            CurrentActorProvider actorProvider
    ) {
        this.stocktakeRepository = stocktakeRepository;
        this.itemRepository = itemRepository;
        this.identificationService = identificationService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    /**
     * Xu ly mot lan quet QR/RFID.
     * Moi lan quet hop le tang so dem them 1.
     */
    @Transactional
    public ScanResult scan(
            UUID stocktakeId,
            UUID itemId,
            String rawCode
    ) {

        AppUser actor = currentUser();

        requireRole(
                actor,
                Role.ADMIN,
                Role.WAREHOUSE_MANAGER,
                Role.WAREHOUSE_STAFF
        );

        String code = normalizeCode(rawCode);

        // Khoa phien de tranh thay doi trang thai dong thoi.
        Stocktake stocktake = stocktakeRepository
                .findByIdForUpdate(stocktakeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "STOCKTAKE_NOT_FOUND",
                                "Khong tim thay phien kiem ke."
                        )
                );

        if (stocktake.getStatus() != StocktakeStatus.IN_PROGRESS) {
            throw new BusinessConflictException(
                    "STOCKTAKE_NOT_IN_PROGRESS",
                    "Phien kiem ke khong cho phep quet ma."
            );
        }

        // Khoa dong san pham dang kiem dem.
        StocktakeItem item = itemRepository
                .findByIdAndStocktakeIdForUpdate(
                        itemId,
                        stocktakeId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "STOCKTAKE_ITEM_NOT_FOUND",
                                "San pham khong thuoc phien kiem ke nay."
                        )
                );

        // Tai su dung module Identification hien tai.
        IdentifierLookupResponse identifier =
                identificationService.lookup(code);

        UUID expectedProductId = item.getProduct().getId();

        // Khong cho phep quet nham san pham.
        if (!identifier.productId().equals(expectedProductId)) {

            throw new InvalidRequestException(
                    "IDENTIFIER_PRODUCT_MISMATCH",
                    "Ma vua quet khong thuoc san pham dang kiem ke."
            );
        }

        // Neu chua dem thi bat dau tu 0.
        // Neu da nhap tay hoac quet truoc do thi cong tiep.
        long previousQuantity =
                item.getActualQuantity() == null
                        ? 0L
                        : item.getActualQuantity();

        final long nextQuantity;

        try {
            nextQuantity = Math.addExact(
                    previousQuantity,
                    1L
            );
        } catch (ArithmeticException exception) {
            throw new BusinessConflictException(
                    "STOCKTAKE_QUANTITY_OVERFLOW",
                    "So luong kiem dem vuot qua gioi han cho phep."
            );
        }

        OffsetDateTime scannedAt = databaseNow();

        // Ghi nhan ket qua kiem dem.
        item.recordCount(
                nextQuantity,
                actor,
                scannedAt,
                item.getNote()
        );

        itemRepository.flush();

        Map<String, Object> oldValues = new LinkedHashMap<>();

        oldValues.put(
                "actualQuantity",
                previousQuantity
        );

        Map<String, Object> newValues = new LinkedHashMap<>();

        newValues.put("actualQuantity", nextQuantity);
        newValues.put("identifierType", identifier.identifierType().name());
        newValues.put("scannedAt", scannedAt);

        // Luu audit log.
        auditPort.record(
                new AuditEvent(
                        "STOCKTAKE_QR_SCANNED",
                        "STOCKTAKE_ITEM",
                        item.getId(),
                        actorProvider.currentActor(),
                        oldValues,
                        newValues
                )
        );

        return new ScanResult(
                stocktakeId,
                item.getId(),
                identifier.productId(),
                identifier.sku(),
                identifier.productName(),
                identifier.identifierType().name(),
                nextQuantity,
                item.getSystemQuantity(),
                item.getDifference(),
                scannedAt
        );
    }

    private AppUser currentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {

            throw new InvalidRequestException(
                    "AUTHENTICATED_USER_REQUIRED",
                    "Ban can dang nhap de quet ma."
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

    private void requireRole(AppUser user, Role... allowedRoles) {

        Role actualRole = user.getRole().getCode();

        for (Role allowed : allowedRoles) {

            if (actualRole == allowed) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Ban khong co quyen quet ma kiem ke."
        );
    }

    private OffsetDateTime databaseNow() {

        return jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                (rs, rowNum) ->
                        rs.getObject(1, OffsetDateTime.class)
        );
    }

    private String normalizeCode(String rawCode) {

        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidRequestException(
                    "IDENTIFIER_REQUIRED",
                    "Ma QR/RFID khong duoc de trong."
            );
        }

        String code = rawCode.trim();

        if (code.length() > 255) {
            throw new InvalidRequestException(
                    "IDENTIFIER_TOO_LONG",
                    "Ma QR/RFID vuot qua 255 ky tu."
            );
        }

        return code;
    }

    public record ScanResult(
            UUID stocktakeId,
            UUID itemId,
            UUID productId,
            String sku,
            String productName,
            String identifierType,
            long actualQuantity,
            long systemQuantity,
            Long difference,
            OffsetDateTime scannedAt
    ) {
    }
}
