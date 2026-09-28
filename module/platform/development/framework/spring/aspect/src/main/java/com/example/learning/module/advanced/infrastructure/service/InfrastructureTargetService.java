package com.example.learning.module.advanced.infrastructure.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class InfrastructureTargetService {

    private final AopTraceLog traceLog;

    public InfrastructureTargetService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String execute() {
        traceLog.add("target:infrastructure");
        return "infrastructure-result";
    }
}
