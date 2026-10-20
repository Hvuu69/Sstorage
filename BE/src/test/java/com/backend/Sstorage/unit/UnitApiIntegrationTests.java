package com.backend.Sstorage.unit;

import com.backend.Sstorage.unit.infrastructure.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnitApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UnitRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void unitCodeIsNormalizedAndCannotBeUpdated() throws Exception {
        mockMvc.perform(post("/api/v1/units")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\" box \",\"name\":\"Thùng\",\"symbol\":\"thùng\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BOX"));
        String id = repository.findAll().get(0).getId().toString();

        mockMvc.perform(put("/api/v1/units/{id}", id)
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\"CHANGED\",\"name\":\"Thùng lớn\",\"symbol\":\"th\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/units/{id}", id)
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Thùng lớn\",\"symbol\":\"th\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BOX"))
                .andExpect(jsonPath("$.name").value("Thùng lớn"));
    }

    @Test
    void adminCannotCreateUnitByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/units")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("{\"code\":\"BOX\",\"name\":\"Thùng\",\"symbol\":\"thùng\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unitHasNoStatusField() throws Exception {
        mockMvc.perform(post("/api/v1/units")
                        .with(user("manager").roles("WAREHOUSE_MANAGER"))
                        .contentType("application/json")
                        .content("{\"code\":\"BOX\",\"name\":\"Thùng\",\"symbol\":\"thùng\",\"active\":true}"))
                .andExpect(status().isBadRequest());
    }
}
