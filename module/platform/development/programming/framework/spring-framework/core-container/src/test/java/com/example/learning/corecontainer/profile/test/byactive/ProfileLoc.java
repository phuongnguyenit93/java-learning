package com.example.learning.corecontainer.profile.test.byactive;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = com.example.learning.SpringCoreContainerApplication.class)
@ActiveProfiles("prd")
public class ProfileLoc {
    @Value("${school.name}")
    String school;

    @Test
    void getProfile() {
        System.out.println(school);
    }
}



