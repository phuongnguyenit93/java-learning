package com.example.learning.module.crosscutting.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class CrossCuttingService {

    private final AopTraceLog traceLog;

    public CrossCuttingService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String createOrder() {
        traceLog.add("target:create-order");
        return "order-created";
    }

    public String cancelOrder() {
        traceLog.add("target:cancel-order");
        return "order-cancelled";
    }
}
