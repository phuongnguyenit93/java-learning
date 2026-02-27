package com.example.learning.controller;

import com.example.learning.component.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScopeController {
    @Autowired
    ScopeSingleton scopeSingleton;

    @Autowired
    ScopePrototype scopePrototype;

    @Autowired
    ScopeRequest scopeRequest;

    @Autowired
    ScopeAllApplication scopeAllApplication;

    @Autowired
    ScopeSession scopeSession;

    @Autowired
    ApplicationContext context;

    @GetMapping("/singleton")
    public void singletonBean(){
        ScopeSingleton scopeSingle1 = context.getBean(ScopeSingleton.class);
        ScopeSingleton scopeSingle2 = context.getBean(ScopeSingleton.class);
        System.out.println("Singleton scope compare : " + (scopeSingle1 == scopeSingle2));
    }

    @GetMapping("/prototype")
    public void prototypeBean(){
        ScopePrototype scopeProto1 = context.getBean(ScopePrototype.class);
        ScopePrototype scopeProto2 = context.getBean(ScopePrototype.class);
        System.out.println("Prototype scope compare : " + (scopeProto1 == scopeProto2));
    }
}
