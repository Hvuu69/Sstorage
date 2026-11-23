package com.backend.Sstorage.inventory;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import com.backend.Sstorage.identification.domain.IdentifierType;
import com.backend.Sstorage.identification.domain.ProductIdentifier;
import com.backend.Sstorage.identification.infrastructure.ProductIdentifierRepository;
import com.backend.Sstorage.inventory.api.infrastructure.InventoryVoucherRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboundVoucherScanApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired InventoryVoucherRepository voucherRepository;
    @Autowired ProductIdentifierRepository identifierRepository;
    @Autowired InventoryBalanceRepository balanceRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UnitRepository unitRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(new Category("INBOUND-SCAN", "Nhập kho QR", null));
        Unit unit = unitRepository.save(new Unit("INBOUND-BOX", "Thùng nhập", "thùng"));
        product = productRepository.save(new Product(category, unit, "IN-SCAN-001", "Sản phẩm nhập quét", null));
        balanceRepository.save(new InventoryBalance(product));
        identifierRepository.save(new ProductIdentifier(product, IdentifierType.QR, "QR-INBOUND-001"));
    }

    @AfterEach
    void cleanUp() {
        voucherRepository.deleteAll();
        identifierRepository.deleteAll();
        balanceRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        unitRepository.deleteAll();
    }

    @Test
    void firstScanAddsLineAndDuplicateScanIncrementsWithoutChangingStock() throws Exception {
        String created = mockMvc.perform(post("/api/v1/imports")
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerName\":\"Nhà cung cấp A\",\"note\":\"\",\"lines\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String voucherId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/imports/{id}/scan", voucherId)
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QR-INBOUND-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("IN-SCAN-001"))
                .andExpect(jsonPath("$.items[0].quantity").value(1));

        mockMvc.perform(post("/api/v1/imports/{id}/scan", voucherId)
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QR-INBOUND-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        assertEquals(0L, balanceRepository.findById(product.getId()).orElseThrow().getQuantityOnHand());
    }

    @Test
    void completedVoucherCannotReceiveMoreScans() throws Exception {
        String created = mockMvc.perform(post("/api/v1/imports")
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerName\":\"Nhà cung cấp A\",\"lines\":[{\"productId\":\""
                                + product.getId() + "\",\"quantity\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        String voucherId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/v1/imports/{id}/complete", voucherId)
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/imports/{id}/scan", voucherId)
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"QR-INBOUND-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INBOUND_VOUCHER_NOT_DRAFT"));
    }
}
