package com.example.learning.component;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class ScopePrototype {
    // Use for every environment
    // 1 instance for every getBean() create or @Autowired used
    // Live when no reference used
    // Use for temporary instance ,  job instance , thread , multi executor

    public ScopePrototype() {
        System.out.println("PrototypeScope scope created");
    }
}
