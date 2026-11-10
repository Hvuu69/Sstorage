package com.backend.Sstorage.security.config;

import com.backend.Sstorage.security.jwt.JwtAuthenticationFilter;
import com.backend.Sstorage.security.user.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    // Cấu hình CORS cho frontend
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH",
                "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept"
        ));

        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider)
            throws Exception {

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"message\":\"Yêu cầu cần được xác thực\"}"
                            );
                        })
                )

                .authenticationProvider(authenticationProvider)

                .authorizeHttpRequests(auth -> auth
                        // ✅ ĐẶC BIỆT QUAN TRỌNG: Cho phép tất cả request OPTIONS (Preflight) đi qua
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 1. API đăng nhập được phép truy cập tự do
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // 2. Các API dành riêng cho ADMIN
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 3. API kho bãi (cũ)
                        .requestMatchers("/api/warehouse/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_STAFF", "WAREHOUSE_MANAGER")

                        // 4. API quản lý (cũ)
                        .requestMatchers("/api/manager/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")

                        // 5. CÁC API MỚI (/api/v1/...)
                        // 5.1. Nhập kho / Xuất kho
                        .requestMatchers("/api/v1/imports/**", "/api/v1/exports/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF")

                        // 5.2. Sản phẩm (ai đã đăng nhập cũng xem được, DEALER cũng cần xem)
                        .requestMatchers("/api/v1/products/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "DEALER")

                        // 5.3. Danh mục & Đơn vị tính
                        .requestMatchers("/api/v1/categories/**", "/api/v1/units/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "DEALER")

                        // 6. Các request khác vẫn phải đăng nhập
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}