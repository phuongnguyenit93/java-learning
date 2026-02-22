package com.example.learning;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class UserDetailEntity {
    private Integer id = 1;
    private String name = "Phuong";
    private Integer age = 31;
    private String country = "Vietnam";
    private String address = "Ho Chi Minh";
}