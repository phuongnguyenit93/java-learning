package com.example.learning.repository;

import com.example.learning.entity.UserAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAdmin,Long> {
    Optional<UserAdmin> findByUsername(String username);
}
