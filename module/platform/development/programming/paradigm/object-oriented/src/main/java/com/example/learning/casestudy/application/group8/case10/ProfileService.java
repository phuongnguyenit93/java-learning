package com.example.learning.casestudy.application.group8.case10;

import com.example.learning.casestudy.model.group8.case10.RequestContext;

public final class ProfileService {
    public String profileFor(RequestContext request) {
        return "profile:" + request.authenticatedUserId();
    }
}
