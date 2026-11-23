package com.backend.Sstorage.inventory;

import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.inventory.api.domain.Supplier;
import com.backend.Sstorage.inventory.api.infrastructure.SupplierRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplierApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired SupplierRepository repository;
    @MockitoSpyBean AuditPort auditPort;

    @BeforeEach
    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void managerCreatesSupplierInDatabaseWithRequiredEmail() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"code":" ncc-001 ","name":" Nhà cung cấp Một ","phone":"0901234567",
                                 "email":"contact@example.com","address":" Hà Nội "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/suppliers/.+")))
                .andExpect(jsonPath("$.code").value("NCC-001"))
                .andExpect(jsonPath("$.email").value("contact@example.com"))
                .andExpect(jsonPath("$.active").value(true));

        assertEquals(1, repository.count());
        assertEquals("contact@example.com", repository.findAll().get(0).getEmail());
        verify(auditPort).record(argThat(event -> event.action().equals("SUPPLIER_CREATED")
                && event.entityType().equals("SUPPLIER")));
    }

    @Test
    void createAndUpdateRejectMissingOrInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\"NCC-EMAIL\",\"name\":\"Nhà cung cấp\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        Supplier supplier = saved("NCC-OLD", "old@example.com");
        mockMvc.perform(put("/api/v1/suppliers/{id}", supplier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Tên mới\",\"email\":\"khong-hop-le\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void listReadsDatabaseWithSearchStatusAndPagination() throws Exception {
        Supplier active = saved("NCC-HANOI", "hanoi@example.com");
        Supplier inactive = saved("NCC-HCM", "hcm@example.com");
        inactive.changeStatus(false);
        repository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/suppliers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .param("search", "hanoi").param("active", "true")
                        .param("page", "0").param("size", "5").param("sort", "supplierCode,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.content[0].email").value("hanoi@example.com"));
    }

    @Test
    void updatePersistsContactAndKeepsCodeImmutable() throws Exception {
        Supplier supplier = saved("NCC-LOCKED", "old@example.com");
        mockMvc.perform(put("/api/v1/suppliers/{id}", supplier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tên mới","phone":"0912345678","email":"new@example.com","address":"Đà Nẵng"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NCC-LOCKED"))
                .andExpect(jsonPath("$.email").value("new@example.com"));

        assertEquals("new@example.com", repository.findById(supplier.getId()).orElseThrow().getEmail());
    }

    @Test
    void statusChangeIsSoftAndThereIsNoDeleteEndpoint() throws Exception {
        Supplier supplier = saved("NCC-STATUS", "status@example.com");
        mockMvc.perform(patch("/api/v1/suppliers/{id}/status", supplier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertFalse(repository.findById(supplier.getId()).orElseThrow().isActive());
        mockMvc.perform(delete("/api/v1/suppliers/{id}", supplier.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void onlyManagerCanManageSuppliers() throws Exception {
        for (String role : new String[]{"WAREHOUSE_STAFF", "DEALER", "ADMIN"}) {
            mockMvc.perform(get("/api/v1/suppliers").with(user(role).roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/suppliers")).andExpect(status().isUnauthorized());
    }

    private Supplier saved(String code, String email) {
        return repository.saveAndFlush(new Supplier(UUID.randomUUID(), code, "Nhà cung cấp " + code,
                null, email, null));
    }
}
