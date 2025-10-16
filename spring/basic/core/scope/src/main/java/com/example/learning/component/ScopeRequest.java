package com.example.learning.component;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = "request",proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ScopeRequest {
    // Use for web request only
    // 1 instance for HTTP Request
    // Live until HTTP Request End
    // Use for request on website, Bean for form data, logging data

    public ScopeRequest() {
        System.out.println("RequestScope scope created");
    }
}
