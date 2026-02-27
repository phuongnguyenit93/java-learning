package com.example.learning.component;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Qualifier("class A")
public class ClassA implements Class {
    @Override
    public String name() {
        return "Class A";
    }
}
