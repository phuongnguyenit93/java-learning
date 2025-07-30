package com.example.learning.service;

import com.example.learning.properties.PrefixProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PrefixService {
    @Autowired
    PrefixProperties prefixProperties;

    public void getPropertiesByPrefix() {
        System.out.println(prefixProperties.getSingle());
        System.out.println(prefixProperties.getListing());
    }
}
