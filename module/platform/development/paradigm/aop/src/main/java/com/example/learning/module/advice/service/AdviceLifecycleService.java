package com.example.learning.module.advice.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class AdviceLifecycleService {

    private final AopTraceLog traceLog;

    public AdviceLifecycleService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String success() {
        traceLog.add("target:success");
        return "success-result";
    }

    public String failure() {
        traceLog.add("target:failure");
        throw new IllegalStateException("intentional-advice-demo-error");
    }
}
