package com.example.learning.controller;

import com.example.learning.service.EnvironmentService;
import com.example.learning.service.PrefixService;
import com.example.learning.service.ResourceService;
import com.example.learning.service.ValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PropertiesController {
    @Autowired
    EnvironmentService envService;

    @Autowired
    PrefixService prefixService;

    @Autowired
    ValueService valueService;

    @Autowired
    ResourceService resourceService;


    @GetMapping("/env")
    public void getPropertiesByEnv() {
        envService.getEnvProperties();
    }

    @GetMapping("/prefix")
    public void getPropertiesByPrefix() {
        prefixService.getPropertiesByPrefix();
    }

    @GetMapping("/value")
    public void getPropertiesByValue() {
        valueService.getPropertiesByValue();
    }

    @GetMapping("/resource")
    public void getPropertiesByResource() {
        resourceService.getPropertiesByResource();
    }
}
