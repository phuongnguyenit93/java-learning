package com.example.learning.module.terminology.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class TerminologyService {

    private final AopTraceLog traceLog;

    public TerminologyService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String execute() {
        traceLog.add("target:TerminologyService.execute");
        return "terminology-target-result";
    }
}
