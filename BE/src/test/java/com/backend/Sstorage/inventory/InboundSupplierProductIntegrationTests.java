package com.backend.Sstorage.inventory;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.identification.domain.*;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.inventory.api.infrastructure.SupplierRepository;
import com.backend.Sstorage.inventory.inbound.domain.SupplierProduct;
import com.backend.Sstorage.inventory.inbound.infrastructure.*;
import com.backend.Sstorage.product.domain.*;
import com.backend.Sstorage.product.infrastructure.*;
import com.backend.Sstorage.security.user.*;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboundSupplierProductIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired SupplierRepository supplierRepository;
    @Autowired SupplierProductRepository supplierProductRepository;
    @Autowired InboundReceiptRepository receiptRepository;
    @Autowired ProductIdentifierRepository identifierRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private Supplier supplier;
    private Product linkedProduct;
    private Product unlinkedProduct;
    private SupplierProduct link;

    @BeforeEach
    void setUp() {
        UserRole staffRole = roleRepository.save(new UserRole(Role.WAREHOUSE_STAFF, "Nhân viên kho"));
        userRepository.save(new AppUser(staffRole, null, "Nhân viên", "staff@example.com",
                passwordEncoder.encode("Password123!"), UserStatus.ACTIVE));
        Category category = categoryRepository.save(new Category("INBOUND", "Nhập kho", null));
        Unit unit = unitRepository.save(new Unit("BOX-IN", "Thùng", "thùng"));
        linkedProduct = productRepository.save(new Product(category, unit, "IN-001", "Sản phẩm liên kết", null));
        unlinkedProduct = productRepository.save(new Product(category, unit, "IN-002", "Sản phẩm ngoài NCC", null));
        balanceRepository.save(new InventoryBalance(linkedProduct));
        balanceRepository.save(new InventoryBalance(unlinkedProduct));
        supplier = supplierRepository.save(new Supplier(UUID.randomUUID(), "NCC-001", "Nhà cung cấp Một", true));
        link = supplierProductRepository.save(new SupplierProduct(supplier, linkedProduct));
        identifierRepository.save(new ProductIdentifier(linkedProduct, IdentifierType.QR, "QR-INBOUND-001"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from public.inventory_transactions");
        receiptRepository.deleteAll();
        supplierProductRepository.deleteAll();
        identifierRepository.deleteAll();
        balanceRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        categoryRepository.deleteAll();
        unitRepository.deleteAll();
    }

    @Test
    void catalogReturnsOnlyActiveLinkedProductsAndIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/active").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(supplier.getId().toString()));

        mockMvc.perform(get("/api/v1/suppliers/{id}/products", supplier.getId()).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/suppliers/{id}/products", supplier.getId())
                        .with(staff()).param("search", "IN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(linkedProduct.getId().toString()))
                .andExpect(jsonPath("$.content[0].identifierValue").value("QR-INBOUND-001"))
                .andExpect(jsonPath("$.content[?(@.sku == 'IN-002')]").isEmpty());
    }

    @Test
    void inactiveSupplierProductOrLinkCannotBeSelected() throws Exception {
        supplier.changeStatus(false);
        supplierRepository.saveAndFlush(supplier);
        mockMvc.perform(get("/api/v1/suppliers/{id}/products", supplier.getId()).with(staff()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SUPPLIER_INACTIVE"));

        supplier.changeStatus(true);
        supplierRepository.saveAndFlush(supplier);
        linkedProduct.changeStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(linkedProduct);
        mockMvc.perform(get("/api/v1/suppliers/{id}/products", supplier.getId()).with(staff()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));

        linkedProduct.changeStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(linkedProduct);
        link.changeStatus(false);
        supplierProductRepository.saveAndFlush(link);
        mockMvc.perform(get("/api/v1/suppliers/{id}/products", supplier.getId()).with(staff()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void missingSupplierAndUnlinkedProductAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/{id}/products", UUID.randomUUID()).with(staff()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("SUPPLIER_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/imports").with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(supplier.getId(), unlinkedProduct.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_SUPPLIED_BY_SUPPLIER"));
    }

    @Test
    void draftUpdateAndQrScanCannotBypassSupplierRelationship() throws Exception {
        String id = createDraft();
        mockMvc.perform(put("/api/v1/imports/{id}", id).with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(supplier.getId(), unlinkedProduct.getId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_SUPPLIED_BY_SUPPLIER"));

        identifierRepository.save(new ProductIdentifier(unlinkedProduct, IdentifierType.QR, "QR-UNLINKED"));
        mockMvc.perform(post("/api/v1/imports/{id}/scan", id).with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QR-UNLINKED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_SUPPLIED_BY_SUPPLIER"));
    }

    @Test
    void linkedQrScanAddsAndThenIncrementsTheExistingProduct() throws Exception {
        String id = createDraft();
        mockMvc.perform(post("/api/v1/imports/{id}/scan", id).with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QR-INBOUND-001\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        assertEquals(0L, balanceRepository.findById(linkedProduct.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void draftDoesNotChangeStockAndConfirmationUpdatesExactlyOnce() throws Exception {
        String id = createDraft();
        assertEquals(0L, balanceRepository.findById(linkedProduct.getId()).orElseThrow().getQuantityOnHand());
        mockMvc.perform(patch("/api/v1/imports/{id}/complete", id).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.supplierCode").value("NCC-001"))
                .andExpect(jsonPath("$.createdByName").value("Nhân viên"))
                .andExpect(jsonPath("$.confirmedByName").value("Nhân viên"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());
        assertEquals(2L, balanceRepository.findById(linkedProduct.getId()).orElseThrow().getQuantityOnHand());
        mockMvc.perform(patch("/api/v1/imports/{id}/complete", id).with(staff()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INBOUND_NOT_DRAFT"));
        assertEquals(2L, balanceRepository.findById(linkedProduct.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void confirmationRevalidatesSupplierAndLinkButHistoryRemainsReadable() throws Exception {
        String supplierInactiveDraft = createDraft();
        supplier.changeStatus(false);
        supplierRepository.saveAndFlush(supplier);
        mockMvc.perform(patch("/api/v1/imports/{id}/complete", supplierInactiveDraft).with(staff()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SUPPLIER_INACTIVE"));

        supplier.changeStatus(true);
        supplierRepository.saveAndFlush(supplier);
        String linkInactiveDraft = createDraft();
        link.changeStatus(false);
        supplierProductRepository.saveAndFlush(link);
        mockMvc.perform(patch("/api/v1/imports/{id}/complete", linkInactiveDraft).with(staff()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_SUPPLIED_BY_SUPPLIER"));

        link.changeStatus(true);
        supplierProductRepository.saveAndFlush(link);
        String historyId = createDraft();
        mockMvc.perform(patch("/api/v1/imports/{id}/complete", historyId).with(staff())).andExpect(status().isOk());
        link.changeStatus(false);
        supplierProductRepository.saveAndFlush(link);
        mockMvc.perform(get("/api/v1/imports/{id}", historyId).with(staff()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].sku").value("IN-001"));
    }

    private String createDraft() throws Exception {
        String body = mockMvc.perform(post("/api/v1/imports").with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(supplier.getId(), linkedProduct.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }
    private String saveBody(UUID supplierId, UUID productId) {
        return "{\"supplierId\":\"" + supplierId + "\",\"note\":\"test\",\"lines\":[{\"productId\":\"" + productId + "\",\"quantity\":2}]}";
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor staff() {
        return user("staff@example.com").roles("WAREHOUSE_STAFF");
    }
}
