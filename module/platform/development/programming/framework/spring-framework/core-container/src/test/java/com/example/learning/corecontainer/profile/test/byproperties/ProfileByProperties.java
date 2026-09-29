package com.example.learning.corecontainer.profile.test.byproperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = com.example.learning.SpringCoreContainerApplication.class)
public class ProfileByProperties {
    @Value("${school.name}")
    String school;

    @Test
    void getProfile() {
        System.out.println(school);
    }
}



