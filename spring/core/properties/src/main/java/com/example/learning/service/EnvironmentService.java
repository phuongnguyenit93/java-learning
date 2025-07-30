package com.example.learning.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentService {
    @Autowired
    Environment env;

    public void getEnvProperties() {
        System.out.println("env.param1 : " + env.getProperty("env.param1",""));
        System.out.println("env.param2 : " + env.getProperty("env.param2",""));
    }
}
