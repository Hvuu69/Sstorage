package com.backend.Sstorage.identification.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.identification.api.dto.CreateIdentifierRequest;
import com.backend.Sstorage.identification.api.dto.IdentifierLookupResponse;
import com.backend.Sstorage.identification.api.dto.IdentifierRegistrationType;
import com.backend.Sstorage.identification.api.dto.IdentifierResponse;
import com.backend.Sstorage.identification.api.dto.IdentifierStatusRequest;
import com.backend.Sstorage.identification.api.dto.QrCodeResponse;
import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentificationService {
    private static final String GENERATED_PREFIX = "SWP:";
    private final ProductIdentifierRepository identifierRepository;
    private final ProductRepository productRepository;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public IdentificationService(ProductIdentifierRepository identifierRepository,
                                 ProductRepository productRepository,
                                 AuditPort auditPort,
                                 CurrentActorProvider actorProvider) {
        this.identifierRepository = identifierRepository;
        this.productRepository = productRepository;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public IdentifierLookupResponse lookup(String rawCode) {
        String code = normalizeRequired(rawCode);
        List<ProductIdentifier> matches = identifierRepository.findAllByValue(code);
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("IDENTIFIER_NOT_FOUND", "Không tìm thấy sản phẩm với mã đã nhập.");
        }
        if (matches.size() > 1) {
            throw new BusinessConflictException("IDENTIFIER_AMBIGUOUS",
                    "Giá trị mã đang được gán cho nhiều loại mã. Cần xử lý dữ liệu trước khi tra cứu.");
        }
        ProductIdentifier identifier = matches.get(0);
        if (!identifier.isActive()) {
            throw new BusinessConflictException("IDENTIFIER_INACTIVE", "Mã nhận diện không còn hiệu lực.");
        }
        if (identifier.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessConflictException("PRODUCT_INACTIVE", "Sản phẩm của mã đã ngừng hoạt động.");
        }
        return IdentifierLookupResponse.from(identifier);
    }

    @Transactional(readOnly = true)
    public List<IdentifierResponse> list(UUID productId) {
        findProduct(productId);
        return identifierRepository.findAllByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(IdentifierResponse::from)
                .toList();
    }

    @Transactional
    public IdentifierResponse create(UUID productId, CreateIdentifierRequest request) {
        Product product = findProduct(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new InvalidRequestException("PRODUCT_INACTIVE", "Không thể gán mã cho sản phẩm đã ngừng hoạt động.");
        }
        if (request.type() == IdentifierRegistrationType.BARCODE) {
            validateBarcode(request.value());
            throw new BusinessConflictException("BARCODE_SCHEMA_MIGRATION_REQUIRED",
                    "Cơ sở dữ liệu chưa cho phép loại BARCODE. Cần duyệt migration trước khi đăng ký.");
        }

        String value = normalizeOptional(request.value());
        if (value == null) {
            value = GENERATED_PREFIX + productId + ":" + UUID.randomUUID();
        }
        ensureGloballyUnique(value);
        ProductIdentifier saved = identifierRepository.saveAndFlush(
                new ProductIdentifier(product, IdentifierType.QR, value));
        audit("PRODUCT_IDENTIFIER_CREATED", saved, null, snapshot(saved));
        return IdentifierResponse.from(saved);
    }

    @Transactional
    public IdentifierResponse changeStatus(UUID id, IdentifierStatusRequest request) {
        ProductIdentifier identifier = findIdentifier(id);
        if (request.active() && identifier.getProduct().getStatus() != ProductStatus.ACTIVE) {
            throw new InvalidRequestException("PRODUCT_INACTIVE",
                    "Không thể kích hoạt mã của sản phẩm đã ngừng hoạt động.");
        }
        Map<String, Object> oldValues = snapshot(identifier);
        identifier.changeStatus(request.active());
        identifierRepository.flush();
        audit("PRODUCT_IDENTIFIER_STATUS_CHANGED", identifier, oldValues, snapshot(identifier));
        return IdentifierResponse.from(identifier);
    }

    @Transactional(readOnly = true)
    public QrCodeResponse qrCode(UUID productId, UUID identifierId) {
        Product product = findProduct(productId);
        ProductIdentifier identifier;
        if (identifierId == null) {
            identifier = identifierRepository
                    .findFirstByProductIdAndTypeAndActiveTrueOrderByCreatedAtDesc(productId, IdentifierType.QR)
                    .orElseThrow(() -> new ResourceNotFoundException("ACTIVE_QR_NOT_FOUND",
                            "Sản phẩm chưa có mã QR đang hoạt động."));
        } else {
            identifier = findIdentifier(identifierId);
            if (!identifier.getProduct().getId().equals(productId) || identifier.getType() != IdentifierType.QR) {
                throw new ResourceNotFoundException("QR_NOT_FOUND", "Mã QR không thuộc sản phẩm này.");
            }
            if (!identifier.isActive()) {
                throw new BusinessConflictException("IDENTIFIER_INACTIVE", "Mã QR không còn hiệu lực.");
            }
        }
        return new QrCodeResponse(productId, identifier.getId(), identifier.getValue(),
                safeFileName(product.getSku()) + "-qr.svg");
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm " + id + "."));
    }

    private ProductIdentifier findIdentifier(UUID id) {
        return identifierRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("IDENTIFIER_NOT_FOUND", "Không tìm thấy mã nhận diện " + id + "."));
    }

    private void ensureGloballyUnique(String value) {
        if (identifierRepository.existsByValue(value)) {
            throw new BusinessConflictException("IDENTIFIER_EXISTS", "Mã nhận diện đã được sử dụng.");
        }
    }

    private void validateBarcode(String value) {
        String normalized = normalizeRequired(value);
        if (!BarcodeValidator.isValidEan13OrUpcA(normalized)) {
            throw new InvalidRequestException("BARCODE_INVALID",
                    "Barcode phải là EAN-13 hoặc UPC-A có check digit hợp lệ.");
        }
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new InvalidRequestException("IDENTIFIER_REQUIRED", "Mã nhận diện là bắt buộc.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeFileName(String sku) {
        return sku.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private Map<String, Object> snapshot(ProductIdentifier identifier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("productId", identifier.getProduct().getId());
        values.put("identifierType", identifier.getType());
        values.put("identifierValue", identifier.getValue());
        values.put("active", identifier.isActive());
        return values;
    }

    private void audit(String action, ProductIdentifier identifier,
                       Map<String, Object> oldValues, Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "PRODUCT_IDENTIFIER", identifier.getId(),
                actorProvider.currentActor(), oldValues, newValues));
    }
}
