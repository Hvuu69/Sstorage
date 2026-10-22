package com.backend.Sstorage.dealerrequest;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.dealer.domain.Dealer;
import com.backend.Sstorage.dealer.infrastructure.DealerRepository;
import com.backend.Sstorage.dealerrequest.application.OutboundRequestReferencePort;
import com.backend.Sstorage.dealerrequest.domain.StockRequest;
import com.backend.Sstorage.dealerrequest.domain.StockRequestStatus;
import com.backend.Sstorage.dealerrequest.infrastructure.StockRequestItemRepository;
import com.backend.Sstorage.dealerrequest.infrastructure.StockRequestRepository;
import com.backend.Sstorage.product.domain.InventoryBalance;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
import com.backend.Sstorage.product.infrastructure.InventoryBalanceRepository;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;
import com.backend.Sstorage.security.user.UserRole;
import com.backend.Sstorage.security.user.UserRoleRepository;
import com.backend.Sstorage.security.user.UserStatus;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DealerRequestApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired StockRequestRepository requestRepository;
    @Autowired StockRequestItemRepository itemRepository;
    @Autowired DealerRepository dealerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository roleRepository;
    @MockitoBean OutboundRequestReferencePort outboundReferencePort;

    private Dealer dealer;
    private Dealer otherDealer;
    private Product activeProduct;
    private Product otherProduct;
    private AppUser dealerUser;
    private AppUser otherDealerUser;
    private AppUser manager;
    private AppUser staff;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        when(outboundReferencePort.activeOutboundRequestIds()).thenReturn(Set.of());

        UserRole dealerRole = roleRepository.saveAndFlush(new UserRole(Role.DEALER, "Đại lý"));
        UserRole managerRole = roleRepository.saveAndFlush(new UserRole(Role.WAREHOUSE_MANAGER, "Quản lý kho"));
        UserRole staffRole = roleRepository.saveAndFlush(new UserRole(Role.WAREHOUSE_STAFF, "Nhân viên kho"));
        UserRole adminRole = roleRepository.saveAndFlush(new UserRole(Role.ADMIN, "Quản trị"));

        dealer = dealerRepository.saveAndFlush(new Dealer("DL-ONE", "Đại lý Một", "0901234567", null, "Hà Nội"));
        otherDealer = dealerRepository.saveAndFlush(new Dealer("DL-TWO", "Đại lý Hai", "0912345678", null, "Đà Nẵng"));
        dealerUser = userRepository.saveAndFlush(appUser(dealerRole, dealer.getId(), "dealer-one@example.com", "Đại lý Một"));
        otherDealerUser = userRepository.saveAndFlush(appUser(dealerRole, otherDealer.getId(), "dealer-two@example.com", "Đại lý Hai"));
        manager = userRepository.saveAndFlush(appUser(managerRole, null, "manager@example.com", "Quản lý"));
        staff = userRepository.saveAndFlush(appUser(staffRole, null, "staff@example.com", "Nhân viên"));
        admin = userRepository.saveAndFlush(appUser(adminRole, null, "admin@example.com", "Quản trị"));

        Category category = categoryRepository.saveAndFlush(new Category("FOOD", "Thực phẩm", null));
        Unit unit = unitRepository.saveAndFlush(new Unit("BOX", "Thùng", "thùng"));
        activeProduct = productRepository.saveAndFlush(new Product(category, unit, "SP-ONE", "Sản phẩm Một", null));
        otherProduct = productRepository.saveAndFlush(new Product(category, unit, "SP-TWO", "Sản phẩm Hai", null));
        balanceRepository.saveAndFlush(new InventoryBalance(activeProduct));
        balanceRepository.saveAndFlush(new InventoryBalance(otherProduct));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void activeDealerCreatesMultiItemRequestFromAuthenticatedIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/dealer-requests")
                        .with(user(dealerUser)).contentType("application/json")
                        .content(createJson(activeProduct.getId(), 4, otherProduct.getId(), 6)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/dealer-requests/my/.+")))
                .andExpect(jsonPath("$.dealerId").value(dealer.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2));

        StockRequest saved = requestRepository.findDetailedById(requestRepository.findAll().get(0).getId()).orElseThrow();
        assertEquals(dealer.getId(), saved.getDealer().getId());
        assertEquals(dealerUser.getId(), saved.getRequestedBy().getId());
    }

    @Test
    void frontendCannotChooseDealerOrApprovalStatus() throws Exception {
        String original = createJson(activeProduct.getId(), 2);
        String body = original.substring(0, original.length() - 1)
                + ",\"dealerId\":\"" + otherDealer.getId() + "\",\"status\":\"APPROVED\"}";
        mockMvc.perform(post("/api/v1/dealer-requests")
                        .with(user(dealerUser)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        assertEquals(0, requestRepository.count());
    }

    @Test
    void inactiveOrUnlinkedDealerCannotCreateRequest() throws Exception {
        dealer.changeStatus(false);
        dealerRepository.saveAndFlush(dealer);
        mockMvc.perform(post("/api/v1/dealer-requests")
                        .with(user(dealerUser)).contentType("application/json")
                        .content(createJson(activeProduct.getId(), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEALER_INACTIVE"));

        AppUser unlinked = userRepository.saveAndFlush(appUser(dealerUser.getRole(), null,
                "unlinked@example.com", "Chưa liên kết"));
        mockMvc.perform(post("/api/v1/dealer-requests")
                        .with(user(unlinked)).contentType("application/json")
                        .content(createJson(activeProduct.getId(), 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DEALER_ACCOUNT_NOT_LINKED"));
    }

    @Test
    void itemValidationRejectsEmptyDuplicateNonPositiveAndInactiveProducts() throws Exception {
        mockMvc.perform(post("/api/v1/dealer-requests").with(user(dealerUser))
                        .contentType("application/json").content("{\"items\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.items").exists());

        mockMvc.perform(post("/api/v1/dealer-requests").with(user(dealerUser))
                        .contentType("application/json")
                        .content(createJson(activeProduct.getId(), 1, activeProduct.getId(), 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST_PRODUCT"));

        mockMvc.perform(post("/api/v1/dealer-requests").with(user(dealerUser))
                        .contentType("application/json").content(createJson(activeProduct.getId(), 0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        otherProduct.changeStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(otherProduct);
        mockMvc.perform(post("/api/v1/dealer-requests").with(user(dealerUser))
                        .contentType("application/json").content(createJson(otherProduct.getId(), 2)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void dealerOnlyListsAndReadsOwnRequestsEvenWhenUuidIsKnown() throws Exception {
        UUID ownId = createRequest(dealerUser, activeProduct.getId(), 2);
        UUID anotherId = createRequest(otherDealerUser, otherProduct.getId(), 3);

        mockMvc.perform(get("/api/v1/dealer-requests/my").with(user(dealerUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownId.toString()));
        mockMvc.perform(get("/api/v1/dealer-requests/my/{id}", anotherId).with(user(dealerUser)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("STOCK_REQUEST_NOT_FOUND"));
    }

    @Test
    void managerListsFiltersAndApprovesWithoutChangingInventoryOrCreatingOutbound() throws Exception {
        UUID requestId = createRequest(dealerUser, activeProduct.getId(), 5);
        long quantityBefore = balanceRepository.findById(activeProduct.getId()).orElseThrow().getQuantityOnHand();

        mockMvc.perform(get("/api/v1/dealer-requests").with(user(manager))
                        .param("search", "DL-ONE").param("dealerId", dealer.getId().toString())
                        .param("status", "PENDING").param("page", "0").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/dealer-requests/{id}/approve", requestId).with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(manager.getId().toString()))
                .andExpect(jsonPath("$.reviewedAt").exists());

        assertEquals(quantityBefore,
                balanceRepository.findById(activeProduct.getId()).orElseThrow().getQuantityOnHand());
        assertEquals(1, requestRepository.count());
    }

    @Test
    void rejectRequiresReasonAndReviewedRequestCannotBeProcessedAgain() throws Exception {
        UUID id = createRequest(dealerUser, activeProduct.getId(), 2);

        mockMvc.perform(post("/api/v1/dealer-requests/{id}/reject", id).with(user(manager))
                        .contentType("application/json").content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());

        mockMvc.perform(post("/api/v1/dealer-requests/{id}/reject", id).with(user(manager))
                        .contentType("application/json").content("{\"reason\":\"Thông tin chưa phù hợp\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Thông tin chưa phù hợp"));

        mockMvc.perform(post("/api/v1/dealer-requests/{id}/approve", id).with(user(manager)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_REQUEST_NOT_PENDING"));
    }

    @Test
    void repeatedAndConcurrentApprovalProduceOnlyOneSuccessfulTransition() throws Exception {
        UUID id = createRequest(dealerUser, activeProduct.getId(), 2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcResult> approve = () -> {
            start.await();
            return mockMvc.perform(post("/api/v1/dealer-requests/{id}/approve", id).with(user(manager)))
                    .andReturn();
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(approve);
            var second = executor.submit(approve);
            start.countDown();
            int firstStatus = first.get().getResponse().getStatus();
            int secondStatus = second.get().getResponse().getStatus();
            assertEquals(Set.of(200, 409), Set.of(firstStatus, secondStatus));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(StockRequestStatus.APPROVED, requestRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void approvedStaffFeedExcludesRequestsWithActiveOutbound() throws Exception {
        UUID included = createRequest(dealerUser, activeProduct.getId(), 2);
        approve(included);
        UUID excluded = createRequest(otherDealerUser, otherProduct.getId(), 3);
        approve(excluded);
        when(outboundReferencePort.activeOutboundRequestIds()).thenReturn(Set.of(excluded));

        mockMvc.perform(get("/api/v1/dealer-requests/approved").with(user(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(included.toString()))
                .andExpect(jsonPath("$.content[0].items.length()").value(1));
    }

    @Test
    void rbacAndNoDeleteEndpointAreEnforced() throws Exception {
        UUID id = createRequest(dealerUser, activeProduct.getId(), 2);

        for (AppUser forbidden : new AppUser[]{staff, admin, dealerUser}) {
            mockMvc.perform(post("/api/v1/dealer-requests/{id}/approve", id).with(user(forbidden)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/dealer-requests").with(user(staff))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dealer-requests/approved").with(user(manager))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dealer-requests/my").with(user(manager))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dealer-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/dealer-requests/{id}", id).with(user(manager)))
                .andExpect(status().isMethodNotAllowed());
        assertEquals(1, requestRepository.count());
    }

    private void approve(UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/dealer-requests/{id}/approve", id).with(user(manager)))
                .andExpect(status().isOk());
    }

    private UUID createRequest(AppUser principal, UUID productId, long quantity) throws Exception {
        Set<UUID> before = requestRepository.findAll().stream().map(StockRequest::getId).collect(java.util.stream.Collectors.toSet());
        mockMvc.perform(post("/api/v1/dealer-requests").with(user(principal))
                        .contentType("application/json").content(createJson(productId, quantity)))
                .andExpect(status().isCreated());
        return requestRepository.findAll().stream().map(StockRequest::getId)
                .filter(id -> !before.contains(id)).findFirst().orElseThrow();
    }

    private AppUser appUser(UserRole role, UUID dealerId, String email, String name) {
        return new AppUser(role, dealerId, name, email, "$2a$10$test-only-not-a-secret", UserStatus.ACTIVE);
    }

    private String createJson(UUID productId, long quantity) {
        return """
                {"items":[{"productId":"%s","quantity":%d}],"note":"Bổ sung hàng"}
                """.formatted(productId, quantity).trim();
    }

    private String createJson(UUID firstProduct, long firstQuantity, UUID secondProduct, long secondQuantity) {
        return """
                {"items":[{"productId":"%s","quantity":%d},{"productId":"%s","quantity":%d}],
                 "note":"Bổ sung nhiều mặt hàng"}
                """.formatted(firstProduct, firstQuantity, secondProduct, secondQuantity).trim();
    }

    private void cleanDatabase() {
        itemRepository.deleteAll();
        requestRepository.deleteAll();
        balanceRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        unitRepository.deleteAll();
        userRepository.deleteAll();
        dealerRepository.deleteAll();
        roleRepository.deleteAll();
    }
}
