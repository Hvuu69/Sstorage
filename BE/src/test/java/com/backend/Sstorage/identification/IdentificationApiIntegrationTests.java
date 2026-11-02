package com.backend.Sstorage.identification;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.product.domain.InventoryBalance;
import com.backend.Sstorage.product.domain.Product;
import com.backend.Sstorage.product.domain.ProductStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentificationApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ProductIdentifierRepository identifierRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("SCAN", "Nhận diện", null));
        Unit unit = unitRepository.save(new Unit("BOX", "Thùng", "thùng"));
        product = productRepository.save(new Product(category, unit, "SCAN-001", "Sản phẩm quét", null));
        balanceRepository.save(new InventoryBalance(product));
    }

    @AfterEach
    void cleanUp() {
        identifierRepository.deleteAll();
        balanceRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        unitRepository.deleteAll();
    }

    @Test
    void managerRegistersGeneratedQrAndLooksItUpWithoutStock() throws Exception {
        String body = mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"QR\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/identifiers/")))
                .andExpect(jsonPath("$.identifierType").value("QR"))
                .andExpect(jsonPath("$.identifierValue", org.hamcrest.Matchers.startsWith("SWP:")))
                .andReturn().getResponse().getContentAsString();

        String code = com.jayway.jsonpath.JsonPath.read(body, "$.identifierValue");
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", code)
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.sku").value("SCAN-001"))
                .andExpect(jsonPath("$.productName").value("Sản phẩm quét"))
                .andExpect(jsonPath("$.identifierType").value("QR"))
                .andExpect(jsonPath("$.quantityOnHand").doesNotExist());
        assertEquals(0L, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void oneProductCanHaveMultipleQrIdentifiers() throws Exception {
        createQr("QR-SCAN-ONE");
        createQr("QR-SCAN-TWO");

        mockMvc.perform(get("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void duplicateValueIsRejectedEvenAcrossIdentifierTypes() throws Exception {
        identifierRepository.saveAndFlush(new ProductIdentifier(product, IdentifierType.RFID, "SHARED-CODE"));

        mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"QR\",\"value\":\"SHARED-CODE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTIFIER_EXISTS"));
    }

    @Test
    void unknownInactiveIdentifierAndInactiveProductReturnDistinctErrors() throws Exception {
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "MISSING")
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("IDENTIFIER_NOT_FOUND"));

        ProductIdentifier identifier = identifierRepository.saveAndFlush(
                new ProductIdentifier(product, IdentifierType.QR, "QR-INACTIVE"));
        identifier.changeStatus(false);
        identifierRepository.saveAndFlush(identifier);
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "QR-INACTIVE")
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTIFIER_INACTIVE"));

        identifier.changeStatus(true);
        product.changeStatus(ProductStatus.INACTIVE);
        identifierRepository.saveAndFlush(identifier);
        productRepository.saveAndFlush(product);
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "QR-INACTIVE")
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void managerCanDisableAndReenableIdentifierOnlyForActiveProduct() throws Exception {
        ProductIdentifier identifier = identifierRepository.saveAndFlush(
                new ProductIdentifier(product, IdentifierType.QR, "QR-STATUS"));
        mockMvc.perform(patch("/api/v1/identifiers/{id}/status", identifier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        product.changeStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        mockMvc.perform(patch("/api/v1/identifiers/{id}/status", identifier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_INACTIVE"));
    }

    @Test
    void qrMetadataReturnsExactRegisteredContent() throws Exception {
        ProductIdentifier identifier = identifierRepository.saveAndFlush(
                new ProductIdentifier(product, IdentifierType.QR, "QR-DOWNLOAD"));
        mockMvc.perform(get("/api/v1/products/{id}/qr-code", product.getId())
                        .param("identifierId", identifier.getId().toString())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifierValue").value("QR-DOWNLOAD"))
                .andExpect(jsonPath("$.suggestedFileName").value("SCAN-001-qr.svg"));
    }

    @Test
    void barcodeValidationRunsButPersistenceStopsAtMigrationGate() throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BARCODE\",\"value\":\"4006381333932\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BARCODE_INVALID"));

        mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BARCODE\",\"value\":\"4006381333931\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BARCODE_SCHEMA_MIGRATION_REQUIRED"));
    }

    @Test
    void rbacMakesStaffReadOnlyAndDeniesDealerAndAdmin() throws Exception {
        ProductIdentifier identifier = identifierRepository.saveAndFlush(
                new ProductIdentifier(product, IdentifierType.QR, "QR-RBAC"));

        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "QR-RBAC")
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"QR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/identifiers/{id}/status", identifier.getId())
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "QR-RBAC")
                        .with(user("dealer").roles("DEALER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/identifiers/lookup").param("code", "QR-RBAC")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    private void createQr(String value) throws Exception {
        mockMvc.perform(post("/api/v1/products/{id}/identifiers", product.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"QR\",\"value\":\"" + value + "\"}"))
                .andExpect(status().isCreated());
    }
}
