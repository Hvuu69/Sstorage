package com.backend.Sstorage.security.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = email == null ? "" : email.trim();
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Không tìm thấy tài khoản."
                    )
                );
    }
}
