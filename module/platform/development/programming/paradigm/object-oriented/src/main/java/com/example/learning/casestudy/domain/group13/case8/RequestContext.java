package com.example.learning.casestudy.domain.group13.case8;

import java.util.Set;

public record RequestContext(String user, Set<String> roles){
    public RequestContext{
        if (user == null || user.isBlank())throw new IllegalArgumentException();
        roles = Set.copyOf(roles);
    }
    public boolean isAdmin(){
        return roles.contains("ADMIN");
    }
}
