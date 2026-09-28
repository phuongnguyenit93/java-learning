package com.example.learning.module.advanced.proxyfactory.service;

import com.example.learning.module.common.AopTraceLog;

public class GreetingTarget implements GreetingOperations {

    private final AopTraceLog traceLog;

    public GreetingTarget(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Override
    public String greet(String name) {
        traceLog.add("target:greet:" + name);
        return "hello-" + name;
    }

    public String targetOnlyCapability() {
        traceLog.add("target:targetOnlyCapability");
        return "target-only-capability";
    }
}
