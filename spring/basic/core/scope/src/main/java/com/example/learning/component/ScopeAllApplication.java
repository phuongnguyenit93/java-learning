package com.example.learning.component;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("application")
public class ScopeAllApplication {
    // Use for web request only
    // 1 instance for all application (context)
    // Live until application close (server close)
    // Use for config , system setting , cache , resource manager

    public ScopeAllApplication() {
        System.out.println("ApplicationScope scope created");
    }
}
