package com.backend.Sstorage.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.Sstorage.security.user.AppUser;
import com.backend.Sstorage.security.user.Role;
import com.backend.Sstorage.security.user.UserRepository;
import com.backend.Sstorage.security.user.UserRole;
import com.backend.Sstorage.security.user.UserRoleRepository;
import com.backend.Sstorage.security.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserRole managerRole;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        managerRole = roleRepository.save(new UserRole(Role.WAREHOUSE_MANAGER, "Quản lý kho"));
    }

    @Test
    void activeUserCanLoginWithEmailIgnoringCase() throws Exception {
        saveUser("manager@example.com", "Demo@123456", UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"MANAGER@EXAMPLE.COM","password":"Demo@123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("manager@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_MANAGER"))
                .andExpect(jsonPath("$.email").value("manager@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test Manager"))
                .andExpect(jsonPath("$.role").value("WAREHOUSE_MANAGER"))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void authenticatedUserCanRestoreTheirSessionProfile() throws Exception {
        AppUser user = saveUser("manager@example.com", "Demo@123456", UserStatus.ACTIVE);
        String loginJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"manager@example.com","password":"Demo@123456"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("manager@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test Manager"))
                .andExpect(jsonPath("$.role").value("WAREHOUSE_MANAGER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void sessionProfileRequiresAValidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyUsernameJsonFieldStillAcceptsAnEmail() throws Exception {
        saveUser("manager@example.com", "Demo@123456", UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"manager@example.com","password":"Demo@123456"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorizedResponse() throws Exception {
        saveUser("manager@example.com", "Demo@123456", UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"manager@example.com","password":"incorrect"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"message":"Tên đăng nhập hoặc mật khẩu không đúng"}
                        """));
    }

    @Test
    void unknownEmailReturnsTheSameGenericUnauthorizedResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"Demo@123456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng"));
    }

    @Test
    void lockedAndDisabledUsersCannotLogin() throws Exception {
        saveUser("locked@example.com", "Demo@123456", UserStatus.LOCKED);
        saveUser("disabled@example.com", "Demo@123456", UserStatus.DISABLED);

        assertLoginIsUnauthorized("locked@example.com");
        assertLoginIsUnauthorized("disabled@example.com");
    }

    private AppUser saveUser(String email, String rawPassword, UserStatus status) {
        return userRepository.save(new AppUser(
                managerRole,
                null,
                "Test Manager",
                email,
                passwordEncoder.encode(rawPassword),
                status
        ));
    }

    private void assertLoginIsUnauthorized(String email) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Demo@123456"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng"));
    }
}
