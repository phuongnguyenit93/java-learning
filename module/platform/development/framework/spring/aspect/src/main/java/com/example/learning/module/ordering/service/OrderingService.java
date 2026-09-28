package com.example.learning.module.ordering.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class OrderingService {

    private final AopTraceLog traceLog;

    public OrderingService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String execute() {
        traceLog.add("target:ordering");
        return "ordering-result";
    }
}
