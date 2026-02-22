package com.example.learning.component;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class ScopeSession {
    // Use for web request only
    // 1 instance for 1 user/session
    // Live until session end /logout
    // Use for user session , card , user reference

    public ScopeSession() {
        System.out.println("SessionScope scope created");
    }
}
