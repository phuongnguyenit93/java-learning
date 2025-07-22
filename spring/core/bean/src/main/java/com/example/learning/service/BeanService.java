package com.example.learning.service;

import com.example.learning.component.Class;
import com.example.learning.model.School;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class BeanService {
    @Autowired
    School schoolDefault;

    @Autowired
    @Qualifier("schoolA")
    School schoolA;

    @Autowired
    @Qualifier("schoolB")
    School schoolB;

    @Autowired
    Class classDefault;

    @Autowired
    @Qualifier("classA")
    Class classA;

    @Autowired
    @Qualifier("classB")
    Class classB;

    public void getSchoolByBean() {
        System.out.println(schoolDefault.getName());
        System.out.println(schoolA.getName());
        System.out.println(schoolB.getName());
    }

    public void getClassByComponent() {
        System.out.println(classDefault.name());
        System.out.println(classA.name());
        System.out.println(classB.name());
    }
}
