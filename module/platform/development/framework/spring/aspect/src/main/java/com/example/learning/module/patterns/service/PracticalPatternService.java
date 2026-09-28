package com.example.learning.module.patterns.service;

import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.patterns.annotation.AuditedOperation;
import org.springframework.stereotype.Service;

@Service
public class PracticalPatternService {

    private final AopTraceLog traceLog;

    public PracticalPatternService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @AuditedOperation(action = "checkout")
    public String checkout(String item) {
        traceLog.add("target:checkout:item=" + item);
        return "checked-out:" + item;
    }
}
