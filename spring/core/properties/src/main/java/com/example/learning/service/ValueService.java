package com.example.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValueService {

    @Value("${prefix.single}")
    private String valueSingle;

    @Value("#{'${prefix.listing}'.split(',')}")
    private List valueList;

    public void getPropertiesByValue() {
        System.out.println(valueSingle);
        System.out.println(valueList);
    }
}
