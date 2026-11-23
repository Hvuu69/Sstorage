package com.backend.Sstorage.inventory.inbound.application;

import com.backend.Sstorage.common.error.*;
import com.backend.Sstorage.identification.application.IdentificationService;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.inventory.api.infrastructure.SupplierRepository;
import com.backend.Sstorage.inventory.inbound.api.InboundDtos.*;
import com.backend.Sstorage.inventory.inbound.domain.*;
import com.backend.Sstorage.inventory.inbound.infrastructure.*;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.*;
import com.backend.Sstorage.security.user.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class InboundService {
    private final InboundReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductRepository productRepository;
    private final ProductIdentifierRepository identifierRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final IdentificationService identificationService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public InboundService(InboundReceiptRepository receiptRepository,
                          SupplierRepository supplierRepository,
                          SupplierProductRepository supplierProductRepository,
                          ProductRepository productRepository,
                          ProductIdentifierRepository identifierRepository,
                          InventoryBalanceRepository balanceRepository,
                          IdentificationService identificationService,
                          UserRepository userRepository,
                          JdbcTemplate jdbcTemplate) {
        this.receiptRepository = receiptRepository;
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.productRepository = productRepository;
        this.identifierRepository = identifierRepository;
        this.balanceRepository = balanceRepository;
        this.identificationService = identificationService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierSummary> activeSuppliers(String rawSearch, Pageable pageable) {
        String search = normalize(rawSearch);
        Page<Supplier> page = search == null
                ? supplierRepository.findByActiveTrue(pageable)
                : supplierRepository.searchActive(searchPattern(search), pageable);
        return page(page.map(s -> new SupplierSummary(s.getId(), s.getSupplierCode(), s.getName())));
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierProductResponse> supplierProducts(UUID supplierId, String rawSearch, Pageable pageable) {
        Supplier supplier = findSupplier(supplierId);
        if (!supplier.isActive()) throw new BusinessConflictException("SUPPLIER_INACTIVE", "Nhà cung cấp đã ngừng hoạt động.");
        String search = normalize(rawSearch);
        Page<SupplierProduct> links = search == null
                ? supplierProductRepository.findSelectable(supplierId, pageable)
                : supplierProductRepository.searchSelectable(supplierId, searchPattern(search), pageable);
        return page(links.map(link -> toSupplierProduct(link.getProduct())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> list(Pageable pageable) {
        return page(receiptRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse get(UUID id) { return toResponse(receiptRepository.findDetail(id).orElseThrow(this::notFound)); }

    @Transactional
    public ReceiptResponse create(SaveRequest request) {
        Supplier supplier = requireActiveSupplier(request.supplierId());
        InboundReceipt receipt = new InboundReceipt(nextCode(), supplier, currentUser(), normalize(request.note()));
        buildItems(supplier.getId(), request.lines()).forEach(receipt::addItem);
        return toResponse(receiptRepository.saveAndFlush(receipt));
    }

    @Transactional
    public ReceiptResponse update(UUID id, SaveRequest request) {
        InboundReceipt receipt = draftForUpdate(id);
        Supplier supplier = requireActiveSupplier(request.supplierId());
        receipt.replaceDraft(supplier, normalize(request.note()), buildItems(supplier.getId(), request.lines()));
        return toResponse(receiptRepository.saveAndFlush(receipt));
    }

    @Transactional
    public ReceiptResponse scan(UUID id, String code) {
        InboundReceipt receipt = draftForUpdate(id);
        requireActiveSupplier(receipt.getSupplier().getId());
        var identifier = identificationService.lookup(code);
        requireSelectable(receipt.getSupplier().getId(), identifier.productId());
        InboundItem item = receipt.getItems().stream()
                .filter(line -> line.getProduct().getId().equals(identifier.productId()))
                .findFirst().orElse(null);
        if (item == null) item = new InboundItem(findActiveProduct(identifier.productId()), 1L);
        else try { item.increment(); } catch (ArithmeticException exception) {
            throw new BusinessConflictException("INBOUND_QUANTITY_OVERFLOW", "Số lượng sản phẩm vượt quá giới hạn cho phép.");
        }
        if (!receipt.getItems().contains(item)) receipt.addItem(item);
        return toResponse(receiptRepository.saveAndFlush(receipt));
    }

    @Transactional
    public ReceiptResponse confirm(UUID id) {
        InboundReceipt receipt = draftForUpdate(id);
        requireActiveSupplier(receipt.getSupplier().getId());
        if (receipt.getItems().isEmpty()) throw new InvalidRequestException("INBOUND_ITEMS_REQUIRED", "Phiếu nhập phải có ít nhất một sản phẩm.");
        for (InboundItem item : receipt.getItems()) {
            requireSelectable(receipt.getSupplier().getId(), item.getProduct().getId());
            if (item.getQuantity() <= 0) throw new InvalidRequestException("INBOUND_QUANTITY_INVALID", "Số lượng nhập phải lớn hơn 0.");
        }
        receipt.confirm(currentUser(), OffsetDateTime.now());
        receiptRepository.flush();
        for (InboundItem item : receipt.getItems()) {
            var balance = balanceRepository.findByProductIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("INVENTORY_BALANCE_NOT_FOUND", "Không tìm thấy tồn kho sản phẩm."));
            balance.add(item.getQuantity());
            jdbcTemplate.update("insert into public.inventory_transactions (id, product_id, inbound_item_id, quantity_delta, created_at) values (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), item.getProduct().getId(), item.getId(), item.getQuantity(), OffsetDateTime.now());
        }
        return toResponse(receipt);
    }

    @Transactional
    public ReceiptResponse cancel(UUID id) {
        InboundReceipt receipt = draftForUpdate(id);
        receipt.cancel();
        return toResponse(receipt);
    }

    private List<InboundItem> buildItems(UUID supplierId, List<LineRequest> lines) {
        Set<UUID> unique = new HashSet<>();
        List<InboundItem> items = new ArrayList<>();
        for (LineRequest line : lines) {
            if (!unique.add(line.productId())) throw new InvalidRequestException("INBOUND_PRODUCT_DUPLICATE", "Sản phẩm không được lặp trong cùng phiếu nhập.");
            requireSelectable(supplierId, line.productId());
            items.add(new InboundItem(findActiveProduct(line.productId()), line.quantity()));
        }
        return items;
    }

    private void requireSelectable(UUID supplierId, UUID productId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm."));
        if (product.getStatus() != ProductStatus.ACTIVE) throw new BusinessConflictException("PRODUCT_INACTIVE", "Sản phẩm đã ngừng hoạt động.");
        if (!supplierProductRepository.isSelectable(supplierId, productId))
            throw new InvalidRequestException("PRODUCT_NOT_SUPPLIED_BY_SUPPLIER", "Sản phẩm này không thuộc danh sách cung cấp của nhà cung cấp đã chọn.");
    }

    private Product findActiveProduct(UUID id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm."));
        if (product.getStatus() != ProductStatus.ACTIVE) throw new BusinessConflictException("PRODUCT_INACTIVE", "Sản phẩm đã ngừng hoạt động.");
        product.getUnit().getSymbol();
        return product;
    }

    private Supplier requireActiveSupplier(UUID id) {
        Supplier supplier = findSupplier(id);
        if (!supplier.isActive()) throw new BusinessConflictException("SUPPLIER_INACTIVE", "Nhà cung cấp đã ngừng hoạt động.");
        return supplier;
    }
    private Supplier findSupplier(UUID id) { return supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp.")); }
    private InboundReceipt draftForUpdate(UUID id) {
        InboundReceipt receipt = receiptRepository.findForUpdate(id).orElseThrow(this::notFound);
        if (receipt.getStatus() != InboundStatus.DRAFT) throw new BusinessConflictException("INBOUND_NOT_DRAFT", "Chỉ có thể thay đổi phiếu nhập nháp.");
        return receipt;
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("INBOUND_NOT_FOUND", "Không tìm thấy phiếu nhập."); }
    private String nextCode() { return "PN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String searchPattern(String value) { return "%" + value.toLowerCase(Locale.ROOT) + "%"; }

    private AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new InvalidRequestException("AUTHENTICATED_USER_REQUIRED", "Bạn cần đăng nhập.");
        if (auth.getPrincipal() instanceof AppUser user && user.getId() != null) return userRepository.findById(user.getId()).orElseThrow(() -> new InvalidRequestException("USER_NOT_FOUND", "Không tìm thấy tài khoản."));
        return userRepository.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new InvalidRequestException("USER_NOT_FOUND", "Không tìm thấy tài khoản."));
    }

    private SupplierProductResponse toSupplierProduct(Product product) {
        var identifier = identifierRepository.findFirstByProductIdAndActiveTrueOrderByCreatedAtDesc(product.getId()).orElse(null);
        return new SupplierProductResponse(product.getId(), product.getSku(), product.getName(), product.getCategory().getName(),
                product.getUnit().getSymbol(), identifier == null ? null : identifier.getType().name(), identifier == null ? null : identifier.getValue());
    }
    private ReceiptResponse toResponse(InboundReceipt receipt) {
        List<ItemResponse> items = receipt.getItems().stream().map(item -> new ItemResponse(item.getId(), item.getProduct().getId(),
                item.getProduct().getSku(), item.getProduct().getName(), item.getProduct().getUnit().getSymbol(), item.getQuantity())).toList();
        return new ReceiptResponse(receipt.getId(), receipt.getReceiptCode(), receipt.getSupplier().getId(), receipt.getSupplier().getSupplierCode(),
                receipt.getSupplier().getName(), receipt.getStatus().name(), receipt.getNote(), receipt.getCreatedBy().getFullName(),
                receipt.getConfirmedBy() == null ? null : receipt.getConfirmedBy().getFullName(), receipt.getCreatedAt(), receipt.getUpdatedAt(), receipt.getConfirmedAt(),
                items, items.stream().mapToLong(ItemResponse::quantity).sum());
    }
    private <T> PageResponse<T> page(Page<T> page) { return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()); }
}
