package com.example.learning.module.advanced.boundary.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class RuntimeBoundaryService {

    private final AopTraceLog traceLog;

    public RuntimeBoundaryService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String execute() {
        traceLog.add("target:runtime-boundary");
        return "runtime-boundary-result";
    }
}
