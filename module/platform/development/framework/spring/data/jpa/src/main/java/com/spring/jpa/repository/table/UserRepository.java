package com.spring.jpa.repository.table;

import com.spring.jpa.entity.table.UserTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserTableEntity,Integer> {
}
