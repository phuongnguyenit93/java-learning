package com.example.learning.casestudy.application.group9.case12;

import com.example.learning.casestudy.domain.group9.case12.Request;
import com.example.learning.casestudy.port.group9.case12.Handler;
import java.util.List;

public final class TrimDecorator implements Handler {
    public final Handler next;
    public final List<String> trace;
    public TrimDecorator(Handler next, List<String> trace) {
        this.next = next; this.trace = trace;
    }
    public String handle(Request request) {
        trace.add("TrimDecorator");
        return next.handle(new Request(request.body().trim()));
    }
}
