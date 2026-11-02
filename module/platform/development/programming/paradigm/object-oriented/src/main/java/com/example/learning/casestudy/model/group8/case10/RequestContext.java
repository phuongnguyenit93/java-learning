package com.example.learning.casestudy.model.group8.case10;


public record RequestContext(String authenticatedUserId) {
    public RequestContext {
        if (authenticatedUserId == null || authenticatedUserId.isBlank())
            throw new IllegalArgumentException("no authenticated identity");
    }
}
