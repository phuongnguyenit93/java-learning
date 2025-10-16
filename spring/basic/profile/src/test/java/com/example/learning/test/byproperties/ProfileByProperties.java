package com.example.learning.test.byproperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ProfileByProperties {
    @Value("${school.name}")
    String school;

    @Test
    void getProfile() {
        System.out.println(school);
    }
}
