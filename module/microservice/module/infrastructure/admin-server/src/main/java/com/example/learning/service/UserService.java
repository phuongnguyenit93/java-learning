package com.example.learning.service;

import com.example.learning.entity.UserAdmin;
import com.example.learning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAdmin userAdmin = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        System.out.println(userAdmin);
        return User.builder()
                .username(userAdmin.getUsername())
                .password(userAdmin.getPassword()) // Mật khẩu trong DB phải được mã hóa BCrypt
                .authorities(userAdmin.getRole())
                .build();
    }
}
