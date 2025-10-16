package com.example.learning.component;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
public class ScopeSingleton {
    // Use for every environment
    // 1 instance for each Application Context (container)
    // Live for all container life
    // Default scope , use for @Service , @Repository, @Component, @Configuration

    public ScopeSingleton() {
        System.out.println("Singleton scope created");
    }
}
