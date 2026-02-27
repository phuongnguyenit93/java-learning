package com.example.learning.test.byfactory;

import com.example.learning.ProfileApplicationTests;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

public class ProfileForAll {
    @TestFactory
    List<DynamicTest> testAllProfiles() {
        List<String> profiles = Arrays.asList("dev", "loc", "prod");

        return profiles.stream()
                .map(profile -> DynamicTest.dynamicTest("Test profile: " + profile, () -> {
                    // Tạo context với profile tương ứng
                    SpringApplication app = new SpringApplication(ProfileApplicationTests.class);
                    app.setWebApplicationType(WebApplicationType.NONE);
                    app.setAdditionalProfiles(profile);

                    try (ConfigurableApplicationContext context = app.run()) {
                        Environment env = context.getEnvironment();
                        String schoolName = env.getProperty("school.name");

                        System.out.println(schoolName);
                    }
                }))
                .toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "loc", "prod"})
    void testSchoolNameWithDifferentProfiles(String profile) {
        SpringApplication app = new SpringApplication(ProfileApplicationTests.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles(profile);

        try (ConfigurableApplicationContext context = app.run()) {
            Environment env = context.getEnvironment();
            String schoolName = env.getProperty("school.name");

            System.out.println(schoolName);
        }
    }
}
