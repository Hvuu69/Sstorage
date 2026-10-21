
package com.backend.Sstorage.security.auth;

import com.backend.Sstorage.security.jwt.JwtService;
import com.backend.Sstorage.security.user.AppUser;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        request.email().trim(),
                        request.password()
                    )
                );

        AppUser user = (AppUser) authentication.getPrincipal();

        String token = jwtService.generateToken(user);

        var roles = user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());

        return new LoginResponse(
                token,
                "Bearer",
                900,
                user.getUsername(),
                roles
        );
    }
}
