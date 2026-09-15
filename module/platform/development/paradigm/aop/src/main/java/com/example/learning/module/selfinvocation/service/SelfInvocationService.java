package com.example.learning.module.selfinvocation.service;

import com.example.learning.module.annotation.TrackExecution;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class SelfInvocationService {

    private final AopTraceLog traceLog;

    public SelfInvocationService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String outerCallsInner() {
        traceLog.add("target:outer-before-inner");
        String result = innerTrackedMethod();
        traceLog.add("target:outer-after-inner");
        return result;
    }

    @TrackExecution("self-invocation-inner")
    public String innerTrackedMethod() {
        traceLog.add("target:innerTrackedMethod");
        return "inner-result";
    }

    @TrackExecution("final-method")
    public final String finalTrackedMethod() {
        return "final-method-result";
    }
}
