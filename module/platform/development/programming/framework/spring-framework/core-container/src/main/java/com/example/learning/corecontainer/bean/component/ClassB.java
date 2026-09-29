package com.example.learning.corecontainer.bean.component;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("class B")
public class ClassB implements Class {
    @Override
    public String name() {
        return "Class B";
    }
}

