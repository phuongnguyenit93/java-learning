package com.example.learning.service;

import com.example.learning.properties.ResourceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {
    @Autowired
    ResourceProperties resourceProperties;

    public void getPropertiesByResource() {
        System.out.println(resourceProperties.getTag());
        System.out.println(resourceProperties.getTagList());
    }
}
