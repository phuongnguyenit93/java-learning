package com.example.learning.repository.table;

import com.example.learning.entity.table.UserTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserTableEntity,Integer> {
}
