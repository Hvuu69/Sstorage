package com.backend.Sstorage.inventory.stocktake.application;

import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.identification.api.dto.IdentifierLookupResponse;
import com.backend.Sstorage.identification.application.IdentificationService;
import com.backend.Sstorage.inventory.stocktake.domain.Stocktake;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeItem;
import com.backend.Sstorage.inventory.stocktake.domain.StocktakeStatus;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeItemRepository;
import com.backend.Sstorage.inventory.stocktake.infrastructure.StocktakeRepository;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StocktakeScanService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeItemRepository itemRepository;
    private final IdentificationService identificationService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public StocktakeScanService(StocktakeRepository stocktakeRepository,
                                StocktakeItemRepository itemRepository,
                                IdentificationService identificationService,
                                ProductRepository productRepository,
                                UserRepository userRepository) {
        this.stocktakeRepository = stocktakeRepository;
        this.itemRepository = itemRepository;
        this.identificationService = identificationService;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /** QR/RFID/SKU chi xac dinh san pham, khong thay doi actual_quantity. */
    @Transactional(readOnly = true)
    public IdentificationResult identify(UUID stocktakeId, String rawCode) {
        requireRole(currentUser(), Role.WAREHOUSE_MANAGER, Role.WAREHOUSE_STAFF);
        String code = normalizeCode(rawCode);

        Stocktake stocktake = stocktakeRepository.findById(stocktakeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "STOCKTAKE_NOT_FOUND", "Khong tim thay phien kiem ke."));
        if (stocktake.getStatus() != StocktakeStatus.IN_PROGRESS
                && stocktake.getStatus() != StocktakeStatus.RECOUNT_REQUIRED) {
            throw new BusinessConflictException(
                    "STOCKTAKE_NOT_COUNTABLE",
                    "Phien kiem ke khong o trang thai cho phep kiem dem.");
        }

        Product product = productRepository.findBySkuIgnoreCase(code)
                .orElseGet(() -> {
                    IdentifierLookupResponse identifier = identificationService.lookup(code);
                    return productRepository.findById(identifier.productId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "PRODUCT_NOT_FOUND", "Khong tim thay san pham cua ma vua nhap."));
                });

        StocktakeItem item = itemRepository
                .findByStocktake_IdAndProduct_Id(stocktakeId, product.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "STOCKTAKE_ITEM_NOT_FOUND", "San pham khong thuoc phien kiem ke nay."));

        return new IdentificationResult(
                stocktakeId, item.getId(), product.getId(), product.getSku(), product.getName(),
                item.getSystemQuantity(), item.getActualQuantity(), item.getDifference());
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new InvalidRequestException("AUTHENTICATED_USER_REQUIRED", "Ban can dang nhap.");
        }
        if (authentication.getPrincipal() instanceof AppUser principal && principal.getId() != null) {
            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Khong tim thay tai khoan."));
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Khong tim thay tai khoan."));
    }

    private void requireRole(AppUser user, Role... allowedRoles) {
        Role actualRole = user.getRole().getCode();
        for (Role allowed : allowedRoles) {
            if (actualRole == allowed) return;
        }
        throw new AccessDeniedException("Ban khong co quyen kiem dem.");
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidRequestException("IDENTIFIER_REQUIRED", "Ma QR/RFID/SKU khong duoc de trong.");
        }
        String code = rawCode.trim();
        if (code.length() > 255) {
            throw new InvalidRequestException("IDENTIFIER_TOO_LONG", "Ma QR/RFID/SKU vuot qua 255 ky tu.");
        }
        return code;
    }

    public record IdentificationResult(UUID stocktakeId, UUID itemId, UUID productId,
                                       String sku, String productName, long systemQuantity,
                                       Long actualQuantity, Long difference) {
    }
}
