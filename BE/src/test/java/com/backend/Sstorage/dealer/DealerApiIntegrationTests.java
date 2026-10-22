package com.backend.Sstorage.dealer;

import com.backend.Sstorage.common.audit.AuditPort;
import com.backend.Sstorage.dealer.domain.Dealer;
import com.backend.Sstorage.dealer.infrastructure.DealerRepository;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRole;
import com.backend.Sstorage.security.user.UserRoleRepository;
import com.backend.Sstorage.security.user.UserStatus;
import com.backend.Sstorage.security.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class DealerApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @MockitoSpyBean DealerRepository repository;
    @MockitoSpyBean AuditPort auditPort;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void managerCreatesNormalizedDealerWithoutCreatingAccount() throws Exception {
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/api/v1/dealers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"dealerCode":"  dl-001 ","name":" Đại lý Minh Phát ",
                                 "phone":" +84 901 234 567 ","email":"contact@example.com",
                                 "address":" Hà Nội "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/dealers/.+")))
                .andExpect(jsonPath("$.dealerCode").value("DL-001"))
                .andExpect(jsonPath("$.name").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.phone").value("+84 901 234 567"))
                .andExpect(jsonPath("$.active").value(true));

        assertEquals(usersBefore, userRepository.count());
        verify(auditPort).record(argThat(event -> event.action().equals("DEALER_CREATED")
                && event.entityType().equals("DEALER")));
    }

    @Test
    void duplicateCodeIsRejectedAndDatabaseConstraintIsFinalGuard() throws Exception {
        repository.saveAndFlush(dealer("DL-001", "Một"));

        mockMvc.perform(post("/api/v1/dealers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("dl-001", "Hai")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEALER_CODE_EXISTS"));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(dealer("DL-001", "Ba")));
    }

    @Test
    void concurrentDuplicateReturnsDatabaseConflict() throws Exception {
        repository.saveAndFlush(dealer("DL-RACE", "Một"));
        doReturn(false).when(repository).existsByDealerCodeIgnoreCase("DL-RACE");

        mockMvc.perform(post("/api/v1/dealers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content(createJson("dl-race", "Hai")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_CONFLICT"));
    }

    @Test
    void listSupportsSearchStatusPaginationAndDetail() throws Exception {
        Dealer active = repository.saveAndFlush(dealer("DL-HANOI", "Đại lý Hà Nội"));
        Dealer inactive = dealer("DL-HCM", "Đại lý Hồ Chí Minh");
        inactive.changeStatus(false);
        repository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/v1/dealers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .param("search", "hanoi").param("active", "true")
                        .param("page", "0").param("size", "5").param("sort", "dealerCode,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()));

        mockMvc.perform(get("/api/v1/dealers/{id}", active.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealerCode").value("DL-HANOI"));
    }

    @Test
    void updateKeepsDealerCodeImmutableAndRejectsUnsupportedCodeField() throws Exception {
        Dealer dealer = repository.saveAndFlush(dealer("DL-LOCKED", "Tên cũ"));

        mockMvc.perform(put("/api/v1/dealers/{id}", dealer.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tên mới","phone":"0901234567","email":"new@example.com",
                                 "address":"Đà Nẵng"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dealerCode").value("DL-LOCKED"))
                .andExpect(jsonPath("$.name").value("Tên mới"));

        mockMvc.perform(put("/api/v1/dealers/{id}", dealer.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"dealerCode":"DL-CHANGED","name":"Tên mới"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_BODY"));
        assertEquals("DL-LOCKED", repository.findById(dealer.getId()).orElseThrow().getDealerCode());
    }

    @Test
    void validationAndNotFoundUseProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/dealers")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"dealerCode":"","name":"","phone":"abc","email":"invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.dealerCode").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        mockMvc.perform(get("/api/v1/dealers/{id}", UUID.randomUUID())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEALER_NOT_FOUND"));
    }

    @Test
    void legacyDealerWithoutRequiredContactRemainsReadableButMustBeCompletedOnUpdate() throws Exception {
        Dealer legacy = repository.saveAndFlush(dealer("DL-LEGACY", "Đại lý cũ"));

        mockMvc.perform(get("/api/v1/dealers/{id}", legacy.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist());

        mockMvc.perform(put("/api/v1/dealers/{id}", legacy.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Đại lý cũ\",\"phone\":\"\",\"address\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists());
    }

    @Test
    void statusChangePreservesDealerAndNoDeleteEndpointExists() throws Exception {
        Dealer dealer = repository.saveAndFlush(dealer("DL-STATUS", "Đại lý trạng thái"));
        UserRole role = roleRepository.findByCode(Role.DEALER)
                .orElseGet(() -> roleRepository.saveAndFlush(new UserRole(Role.DEALER, "Đại lý")));
        AppUser linkedUser = userRepository.saveAndFlush(new AppUser(role, dealer.getId(), "Người dùng đại lý",
                "linked-" + UUID.randomUUID() + "@example.com", "$2a$10$not-a-login-secret", UserStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/dealers/{id}/status", dealer.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertFalse(repository.findById(dealer.getId()).orElseThrow().isActive());
        assertEquals(dealer.getId(), userRepository.findById(linkedUser.getId()).orElseThrow().getDealerId());
        mockMvc.perform(delete("/api/v1/dealers/{id}", dealer.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isMethodNotAllowed());
        userRepository.deleteById(linkedUser.getId());
    }

    @Test
    void onlyWarehouseManagerCanAccessDealerManagement() throws Exception {
        for (String role : new String[]{"WAREHOUSE_STAFF", "DEALER", "ADMIN"}) {
            mockMvc.perform(get("/api/v1/dealers").with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/dealers")
                            .with(user(role.toLowerCase()).roles(role))
                            .contentType("application/json").content(createJson("DL-" + role, role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/dealers")).andExpect(status().isUnauthorized());
    }

    private Dealer dealer(String code, String name) {
        return new Dealer(code, name, null, null, null);
    }

    private String createJson(String code, String name) {
        return """
                {"dealerCode":"%s","name":"%s","phone":"0901234567",
                 "email":"contact@example.com","address":"Hà Nội"}
                """.formatted(code, name);
    }
}
