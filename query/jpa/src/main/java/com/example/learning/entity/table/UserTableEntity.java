package com.example.learning.entity.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Table(name = "TB_USER", schema = "LEARNING")
@Getter @Setter
@Entity
public class UserTableEntity {
    @Id
    @Column(name = "ID")
    private Integer id;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "PASSWORD")
    private String password;
}
