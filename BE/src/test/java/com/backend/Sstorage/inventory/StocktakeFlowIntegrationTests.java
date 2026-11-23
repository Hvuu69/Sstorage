package com.backend.Sstorage.inventory;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.inventory.stocktake.application.StocktakeScanService;
import com.backend.Sstorage.inventory.stocktake.application.StocktakeService;
import com.backend.Sstorage.inventory.stocktake.infrastructure.InventoryTransactionRepository;
import com.backend.Sstorage.product.domain.InventoryBalance;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.infrastructure.InventoryBalanceRepository;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.security.user.*;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class StocktakeFlowIntegrationTests {

    @Autowired StocktakeService service;
    @Autowired StocktakeScanService scanService;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired ProductIdentifierRepository identifierRepository;
    @Autowired InventoryTransactionRepository transactionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository roleRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private AppUser manager;
    private AppUser staff;
    private AppUser admin;
    private Product product;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        UserRole managerRole = roleRepository.save(new UserRole(Role.WAREHOUSE_MANAGER, "Quản lý kho"));
        UserRole staffRole = roleRepository.save(new UserRole(Role.WAREHOUSE_STAFF, "Nhân viên kho"));
        UserRole adminRole = roleRepository.save(new UserRole(Role.ADMIN, "Quản trị"));
        manager = userRepository.save(new AppUser(managerRole, null, "Manager", "manager-stocktake@example.com", "x", UserStatus.ACTIVE));
        staff = userRepository.save(new AppUser(staffRole, null, "Staff", "staff-stocktake@example.com", "x", UserStatus.ACTIVE));
        admin = userRepository.save(new AppUser(adminRole, null, "Admin", "admin-stocktake@example.com", "x", UserStatus.ACTIVE));

        Category category = categoryRepository.save(new Category("STOCKTAKE", "Kiểm kê", null));
        Unit unit = unitRepository.save(new Unit("EA-STK", "Cái", "cái"));
        product = productRepository.save(new Product(category, unit, "STK-001", "Sản phẩm kiểm kê", null));
        InventoryBalance balance = new InventoryBalance(product);
        balance.add(5);
        balanceRepository.saveAndFlush(balance);
        identifierRepository.saveAndFlush(new ProductIdentifier(product, IdentifierType.QR, "QR-STK-001"));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        cleanDatabase();
    }

    @Test
    void identifyDoesNotCountAndApprovalIsTheOnlyInventoryMutation() {
        authenticate(manager);
        var created = service.create(List.of(product.getId()), "Kiểm kê định kỳ");
        var draft = service.getDetail(created.id());
        assertEquals(1, draft.items().size());
        assertEquals(5, draft.items().get(0).systemQuantity());
        service.start(created.id());

        authenticate(staff);
        var bySku = scanService.identify(created.id(), "STK-001");
        var byQr = scanService.identify(created.id(), "QR-STK-001");
        assertEquals(bySku.itemId(), byQr.itemId());
        assertNull(service.getDetail(created.id()).items().get(0).actualQuantity());
        assertEquals(5, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());

        service.recordCount(created.id(), bySku.itemId(), 7, "Đếm hai lần");
        assertEquals(5, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
        assertEquals("PENDING_CONFIRMATION", service.submit(created.id()).status());
        assertEquals(5, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());

        authenticate(manager);
        var completed = service.approve(created.id());
        assertEquals("COMPLETED", completed.status());
        assertEquals(manager.getId(), completed.approvedBy());
        assertNotNull(completed.approvedAt());
        assertEquals(7, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void managerCanRequestRecountWithoutReplacingSnapshotItems() {
        authenticate(manager);
        var created = service.create(List.of(product.getId()), null);
        service.start(created.id());
        var initial = service.getDetail(created.id());
        var item = initial.items().get(0);

        authenticate(staff);
        service.recordCount(created.id(), item.id(), 5, null);
        service.submit(created.id());

        authenticate(manager);
        assertEquals("RECOUNT_REQUIRED", service.requestRecount(created.id()).status());
        var recount = service.getDetail(created.id()).items().get(0);
        assertEquals(item.id(), recount.id());
        assertEquals(item.systemQuantity(), recount.systemQuantity());
        assertNull(recount.actualQuantity());
        assertNull(recount.countedBy());

        authenticate(staff);
        service.recordCount(created.id(), item.id(), 5, "Đã kiểm lại");
        assertEquals("PENDING_CONFIRMATION", service.submit(created.id()).status());
    }

    @Test
    void adminHasNoStocktakeWritePermission() {
        authenticate(admin);
        assertThrows(AccessDeniedException.class,
                () -> service.create(List.of(product.getId()), null));
    }

    private void authenticate(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities()));
    }

    private void cleanDatabase() {
        jdbcTemplate.update("delete from public.inventory_transactions");
        jdbcTemplate.update("delete from public.stocktake_items");
        jdbcTemplate.update("delete from public.stocktakes");
        jdbcTemplate.update("delete from public.product_identifiers");
        jdbcTemplate.update("delete from public.inventory_balances");
        jdbcTemplate.update("delete from public.products");
        jdbcTemplate.update("delete from public.users");
        jdbcTemplate.update("delete from public.roles");
        jdbcTemplate.update("delete from public.categories");
        jdbcTemplate.update("delete from public.units");
    }
}
