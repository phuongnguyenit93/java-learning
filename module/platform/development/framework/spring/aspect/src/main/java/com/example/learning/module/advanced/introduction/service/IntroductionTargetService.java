package com.example.learning.module.advanced.introduction.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class IntroductionTargetService {

    private final AopTraceLog traceLog;

    public IntroductionTargetService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String businessOperation() {
        traceLog.add("target:introduction-business-operation");
        return "introduction-result";
    }
}
