package com.example.learning.test.byactive;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
public class ProfileDev {
    @Value("${school.name}")
    String school;

    @Test
    void getProfile() {
        System.out.println(school);
    }
}
