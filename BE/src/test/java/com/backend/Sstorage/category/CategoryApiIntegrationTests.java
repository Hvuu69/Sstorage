package com.backend.Sstorage.category;

import com.backend.Sstorage.category.domain.Category;
import com.backend.Sstorage.category.infrastructure.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired CategoryRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void managerCanCreateAndCodeIsNormalized() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(user("manager-id").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("""
                                {"code":"  food ","name":" Thực phẩm ","description":" Hàng tiêu dùng "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/v1/categories/.+")))
                .andExpect(jsonPath("$.code").value("FOOD"))
                .andExpect(jsonPath("$.name").value("Thực phẩm"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void duplicateCodeIgnoringCaseReturnsConflict() throws Exception {
        repository.saveAndFlush(new Category("FOOD", "Thực phẩm", null));
        mockMvc.perform(post("/api/v1/categories")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\"food\",\"name\":\"Khác\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_CODE_EXISTS"));
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void staffCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(user("staff").roles("WAREHOUSE_STAFF"))
                        .contentType("application/json")
                        .content("{\"code\":\"FOOD\",\"name\":\"Thực phẩm\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dealerOnlyReadsActiveCategories() throws Exception {
        Category active = repository.saveAndFlush(new Category("ACTIVE", "Đang dùng", null));
        Category inactive = repository.saveAndFlush(new Category("INACTIVE", "Đã tắt", null));
        mockMvc.perform(patch("/api/v1/categories/{id}/status", inactive.getId())
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories")
                        .with(user("dealer").roles("DEALER"))
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId().toString()));
    }

    @Test
    void databaseUniqueConstraintProtectsCategoryCode() {
        repository.saveAndFlush(new Category("DUPLICATE", "Một", null));
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Category("DUPLICATE", "Hai", null)));
    }
}
