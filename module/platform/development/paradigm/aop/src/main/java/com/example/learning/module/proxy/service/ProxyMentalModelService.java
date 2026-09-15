package com.example.learning.module.proxy.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class ProxyMentalModelService {

    private final AopTraceLog traceLog;

    public ProxyMentalModelService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String invokeTarget() {
        traceLog.add("target:invokeTarget");
        return "target-completed";
    }
}
