package com.example.learning;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

public class EnvironmentUtils {
    @Autowired
    Environment environment;

    public void getProperties() {
        String propTest = environment.getProperty("message.test");
        System.out.println(propTest);
    }
}
