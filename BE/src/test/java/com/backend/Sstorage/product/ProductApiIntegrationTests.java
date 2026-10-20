package com.backend.Sstorage.product;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.product.application.ProductReferencePort;
import com.backend.Sstorage.product.domain.InventoryBalance;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.infrastructure.InventoryBalanceRepository;
import com.backend.Sstorage.product.infrastructure.ProductRepository;
import com.backend.Sstorage.unit.domain.Unit;
import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @MockitoSpyBean ProductRepository productRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;
    @MockitoBean ProductReferencePort referencePort;

    private Category category;
    private Unit unit;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        category = categoryRepository.saveAndFlush(new Category("FOOD", "Thực phẩm", null));
        unit = unitRepository.saveAndFlush(new Unit("BOX", "Thùng", "thùng"));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void managerCreatesNormalizedProductAndZeroBalanceInOneUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("  sp-001 ", category.getId(), unit.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/products/.+")))
                .andExpect(jsonPath("$.sku").value("SP-001"))
                .andExpect(jsonPath("$.quantityOnHand").value(0));

        Product product = productRepository.findAll().get(0);
        assertEquals(0, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void duplicateSkuIsCaseInsensitiveAndDatabaseConstraintRemainsFinalGuard() throws Exception {
        productRepository.saveAndFlush(new Product(category, unit, "SP-001", "Một", null));
        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("sp-001", category.getId(), unit.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_SKU_EXISTS"));

        assertThrows(DataIntegrityViolationException.class, () ->
                productRepository.saveAndFlush(new Product(category, unit, "SP-001", "Hai", null)));
    }

    @Test
    void concurrentDuplicateThatPassesPrecheckReturnsDatabaseConflict() throws Exception {
        productRepository.saveAndFlush(new Product(category, unit, "SP-RACE", "Một", null));
        doReturn(false).when(productRepository).existsBySkuIgnoreCase("SP-RACE");

        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("sp-race", category.getId(), unit.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_CONFLICT"));
    }

    @Test
    void createRejectsMissingOrInactiveCategoryAndMissingUnit() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("SP-MISSING-CATEGORY", missing, unit.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));

        category.changeStatus(false);
        categoryRepository.saveAndFlush(category);
        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("SP-INACTIVE", category.getId(), unit.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_INACTIVE"));

        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("SP-MISSING-UNIT", activeCategory().getId(), missing)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("UNIT_NOT_FOUND"));
    }

    @Test
    void updateRetainsInactiveCurrentCategoryAndNeverAcceptsInventoryQuantity() throws Exception {
        Product product = persistedProduct("SP-EDIT");
        category.changeStatus(false);
        categoryRepository.saveAndFlush(category);

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(updateJson("SP-EDIT", "Tên mới", category.getId(), unit.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tên mới"))
                .andExpect(jsonPath("$.quantityOnHand").value(0));

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(updateJson("SP-EDIT", "Tên mới", category.getId(), unit.getId())
                                .replace("}", ",\"quantityOnHand\":99}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        assertEquals(0, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void skuCannotChangeWhenBusinessReferencesExist() throws Exception {
        Product product = persistedProduct("SP-LOCKED");
        when(referencePort.hasBusinessReferences(product.getId())).thenReturn(true);

        mockMvc.perform(put("/api/v1/products/{id}", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(updateJson("SP-NEW", "Sản phẩm", category.getId(), unit.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_SKU_IMMUTABLE"));
    }

    @Test
    void managerCanSearchFilterAndDeactivateProduct() throws Exception {
        Product product = persistedProduct("SP-FILTER");
        mockMvc.perform(get("/api/v1/products")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .param("search", "filter")
                        .param("categoryId", category.getId().toString())
                        .param("status", "ACTIVE")
                        .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].quantityOnHand").value(0));

        mockMvc.perform(patch("/api/v1/products/{id}/status", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void dealerOnlySeesActiveCatalogAndNeverReceivesStockField() throws Exception {
        Product active = persistedProduct("SP-ACTIVE");
        Product inactive = persistedProduct("SP-INACTIVE");
        inactive.changeStatus(com.backend.Sstorage.product.domain.ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/products")
                        .with(user("dealer").roles("DEALER"))
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].quantityOnHand").doesNotExist());

        mockMvc.perform(get("/api/v1/products/{id}", inactive.getId())
                        .with(user("dealer").roles("DEALER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleMatrixIsEnforcedByBackend() throws Exception {
        Product product = persistedProduct("SP-RBAC");
        mockMvc.perform(get("/api/v1/products/{id}", product.getId())
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(0));

        for (String role : new String[]{"WAREHOUSE_STAFF", "DEALER", "ADMIN"}) {
            mockMvc.perform(post("/api/v1/products")
                            .with(user(role.toLowerCase()).roles(role))
                            .contentType("application/json")
                            .content(createJson("SP-" + role, category.getId(), unit.getId())))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    private Product persistedProduct(String sku) {
        Product product = productRepository.saveAndFlush(new Product(category, unit, sku, "Sản phẩm " + sku, null));
        balanceRepository.saveAndFlush(new InventoryBalance(product));
        return product;
    }

    private Category activeCategory() {
        return categoryRepository.saveAndFlush(new Category("OTHER", "Danh mục khác", null));
    }

    private String createJson(String sku, UUID categoryId, UUID unitId) {
        return """
                {"sku":"%s","name":"Sản phẩm","categoryId":"%s","unitId":"%s","description":"Mô tả"}
                """.formatted(sku, categoryId, unitId);
    }

    private String updateJson(String sku, String name, UUID categoryId, UUID unitId) {
        return """
                {"sku":"%s","name":"%s","categoryId":"%s","unitId":"%s","description":"Mô tả"}
                """.formatted(sku, name, categoryId, unitId).trim();
    }

    private void cleanDatabase() {
        balanceRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        unitRepository.deleteAll();
    }
}
