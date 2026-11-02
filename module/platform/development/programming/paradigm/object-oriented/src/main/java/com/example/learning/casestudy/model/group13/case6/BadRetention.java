package com.example.learning.casestudy.model.group13.case6;

import java.util.ArrayList;
import java.util.List;

public final class BadRetention {
    public final List<Request> everything = new ArrayList<>();
    public void remember(Request request){
        everything.add(request);
    }
}
