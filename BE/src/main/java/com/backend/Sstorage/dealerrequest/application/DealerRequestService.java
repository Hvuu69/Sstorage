package com.backend.Sstorage.dealerrequest.application;

import com.backend.Sstorage.common.audit.AuditEvent;
import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.common.audit.CurrentActorProvider;
import com.backend.Sstorage.common.error.BusinessConflictException;
import com.backend.Sstorage.common.error.InvalidRequestException;
import com.backend.Sstorage.common.error.ResourceNotFoundException;
import com.backend.Sstorage.dealer.domain.Dealer;
import com.backend.Sstorage.dealer.infrastructure.DealerRepository;
import com.backend.Sstorage.dealerrequest.api.dto.CreateStockRequestItem;
import com.backend.Sstorage.dealerrequest.api.dto.CreateStockRequestRequest;
import com.backend.Sstorage.dealerrequest.api.dto.RejectStockRequestRequest;
import com.backend.Sstorage.dealerrequest.api.dto.StockRequestDetailResponse;
import com.backend.Sstorage.dealerrequest.api.dto.StockRequestSummaryResponse;
import com.backend.Sstorage.dealerrequest.domain.StockRequest;
import com.backend.Sstorage.dealerrequest.domain.StockRequestStatus;
import com.backend.Sstorage.dealerrequest.infrastructure.StockRequestRepository;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DealerRequestService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final StockRequestRepository requestRepository;
    private final DealerRepository dealerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OutboundRequestReferencePort outboundReferencePort;
    private final AuditPort auditPort;
    private final CurrentActorProvider actorProvider;

    public DealerRequestService(StockRequestRepository requestRepository, DealerRepository dealerRepository,
                                ProductRepository productRepository, UserRepository userRepository,
                                OutboundRequestReferencePort outboundReferencePort, AuditPort auditPort,
                                CurrentActorProvider actorProvider) {
        this.requestRepository = requestRepository;
        this.dealerRepository = dealerRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.outboundReferencePort = outboundReferencePort;
        this.auditPort = auditPort;
        this.actorProvider = actorProvider;
    }

    @Transactional
    public StockRequestDetailResponse create(CreateStockRequestRequest request) {
        AppUser user = currentUser();
        if (user.getDealerId() == null) {
            throw new InvalidRequestException("DEALER_ACCOUNT_NOT_LINKED",
                    "Tài khoản chưa được liên kết với hồ sơ đại lý.");
        }
        Dealer dealer = dealerRepository.findById(user.getDealerId()).orElseThrow(() ->
                new InvalidRequestException("DEALER_ACCOUNT_NOT_LINKED",
                        "Không tìm thấy hồ sơ đại lý được liên kết với tài khoản."));
        if (!dealer.isActive()) {
            throw new BusinessConflictException("DEALER_INACTIVE",
                    "Đại lý đã ngừng hoạt động và không thể tạo yêu cầu mới.");
        }

        validateNoDuplicateProducts(request.items());
        Map<UUID, Product> products = products(request.items());
        StockRequest stockRequest = new StockRequest(nextRequestCode(), dealer, user,
                normalizeOptional(request.note()));
        for (CreateStockRequestItem item : request.items()) {
            stockRequest.addItem(products.get(item.productId()), item.quantity());
        }
        StockRequest saved = requestRepository.saveAndFlush(stockRequest);
        audit("STOCK_REQUEST_CREATED", saved, null, snapshot(saved));
        return StockRequestDetailResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<StockRequestSummaryResponse> listMine(StockRequestStatus status, Pageable pageable) {
        UUID dealerId = requireDealerId(currentUser());
        return requestRepository.findAll(specification(null, dealerId, status, null, null, Set.of()), pageable)
                .map(StockRequestSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public StockRequestDetailResponse getMine(UUID id) {
        UUID dealerId = requireDealerId(currentUser());
        return StockRequestDetailResponse.from(requestRepository.findDetailedByIdAndDealerId(id, dealerId)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public Page<StockRequestSummaryResponse> listAll(String search, UUID dealerId, StockRequestStatus status,
                                                     LocalDate from, LocalDate to, Pageable pageable) {
        validateDateRange(from, to);
        return requestRepository.findAll(specification(search, dealerId, status, from, to, Set.of()), pageable)
                .map(StockRequestSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public StockRequestDetailResponse get(UUID id) {
        return StockRequestDetailResponse.from(requestRepository.findDetailedById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional(readOnly = true)
    public Page<StockRequestDetailResponse> listApproved(Pageable pageable) {
        Set<UUID> excluded = outboundReferencePort.activeOutboundRequestIds();
        return requestRepository.findAll(specification(null, null, StockRequestStatus.APPROVED,
                        null, null, excluded), pageable)
                .map(StockRequestDetailResponse::from);
    }

    @Transactional
    public StockRequestDetailResponse approve(UUID id) {
        AppUser reviewer = currentUser();
        StockRequest request = pendingForUpdate(id);
        if (!request.getDealer().isActive()) {
            throw new BusinessConflictException("DEALER_INACTIVE",
                    "Không thể phê duyệt yêu cầu của đại lý đã ngừng hoạt động.");
        }
        if (request.getItems().stream().anyMatch(item -> item.getProduct().getStatus() != ProductStatus.ACTIVE)) {
            throw new BusinessConflictException("REQUEST_PRODUCT_INACTIVE",
                    "Yêu cầu chứa sản phẩm đã ngừng hoạt động.");
        }
        Map<String, Object> oldValues = snapshot(request);
        request.review(StockRequestStatus.APPROVED, reviewer, null, OffsetDateTime.now());
        requestRepository.flush();
        audit("STOCK_REQUEST_APPROVED", request, oldValues, snapshot(request));
        return StockRequestDetailResponse.from(request);
    }

    @Transactional
    public StockRequestDetailResponse reject(UUID id, RejectStockRequestRequest rejection) {
        AppUser reviewer = currentUser();
        StockRequest request = pendingForUpdate(id);
        Map<String, Object> oldValues = snapshot(request);
        request.review(StockRequestStatus.REJECTED, reviewer, rejection.reason().trim(), OffsetDateTime.now());
        requestRepository.flush();
        audit("STOCK_REQUEST_REJECTED", request, oldValues, snapshot(request));
        return StockRequestDetailResponse.from(request);
    }

    private StockRequest pendingForUpdate(UUID id) {
        StockRequest request = requestRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new BusinessConflictException("STOCK_REQUEST_NOT_PENDING",
                    "Chỉ yêu cầu đang chờ duyệt mới có thể được xét duyệt.");
        }
        return request;
    }

    private Specification<StockRequest> specification(String search, UUID dealerId, StockRequestStatus status,
                                                       LocalDate from, LocalDate to, Set<UUID> excludedIds) {
        Specification<StockRequest> result = (root, query, builder) -> builder.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            result = result.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("requestCode")), pattern),
                    builder.like(builder.lower(root.get("dealer").get("dealerCode")), pattern),
                    builder.like(builder.lower(root.get("dealer").get("name")), pattern)));
        }
        if (dealerId != null) {
            result = result.and((root, query, builder) -> builder.equal(root.get("dealer").get("id"), dealerId));
        }
        if (status != null) {
            result = result.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (from != null) {
            OffsetDateTime start = from.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
            result = result.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            OffsetDateTime endExclusive = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
            result = result.and((root, query, builder) -> builder.lessThan(root.get("createdAt"), endExclusive));
        }
        if (!excludedIds.isEmpty()) {
            result = result.and((root, query, builder) -> root.get("id").in(excludedIds).not());
        }
        return result;
    }

    private void validateNoDuplicateProducts(List<CreateStockRequestItem> items) {
        Set<UUID> productIds = new HashSet<>();
        if (items.stream().anyMatch(item -> !productIds.add(item.productId()))) {
            throw new InvalidRequestException("DUPLICATE_REQUEST_PRODUCT",
                    "Mỗi sản phẩm chỉ được xuất hiện một lần trong yêu cầu.");
        }
    }

    private Map<UUID, Product> products(List<CreateStockRequestItem> items) {
        List<UUID> ids = items.stream().map(CreateStockRequestItem::productId).toList();
        Map<UUID, Product> products = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID id : ids) {
            Product product = products.get(id);
            if (product == null) {
                throw new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm " + id + ".");
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new InvalidRequestException("PRODUCT_INACTIVE",
                        "Sản phẩm " + product.getSku() + " đã ngừng hoạt động.");
            }
        }
        return products;
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidRequestException("AUTHENTICATED_USER_UNAVAILABLE", "Không xác định được người dùng hiện tại.");
        }
        if (authentication.getPrincipal() instanceof AppUser principal && principal.getId() != null) {
            return userRepository.findById(principal.getId()).orElseThrow(() ->
                    new InvalidRequestException("AUTHENTICATED_USER_UNAVAILABLE",
                            "Không tìm thấy tài khoản hiện tại."));
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).orElseThrow(() ->
                new InvalidRequestException("AUTHENTICATED_USER_UNAVAILABLE", "Không tìm thấy tài khoản hiện tại."));
    }

    private UUID requireDealerId(AppUser user) {
        if (user.getDealerId() == null) {
            throw new InvalidRequestException("DEALER_ACCOUNT_NOT_LINKED",
                    "Tài khoản chưa được liên kết với hồ sơ đại lý.");
        }
        return user.getDealerId();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("INVALID_DATE_RANGE", "Ngày bắt đầu không được sau ngày kết thúc.");
        }
    }

    private String nextRequestCode() {
        return "REQ-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("STOCK_REQUEST_NOT_FOUND", "Không tìm thấy yêu cầu " + id + ".");
    }

    private Map<String, Object> snapshot(StockRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("requestCode", request.getRequestCode());
        values.put("dealerId", request.getDealer().getId());
        values.put("status", request.getStatus());
        values.put("reviewedBy", request.getReviewedBy() == null ? null : request.getReviewedBy().getId());
        values.put("reviewedAt", request.getReviewedAt());
        values.put("itemCount", request.getItems().size());
        return values;
    }

    private void audit(String action, StockRequest request, Map<String, Object> oldValues,
                       Map<String, Object> newValues) {
        auditPort.record(new AuditEvent(action, "STOCK_REQUEST", request.getId(), actorProvider.currentActor(),
                oldValues, newValues));
    }
}
