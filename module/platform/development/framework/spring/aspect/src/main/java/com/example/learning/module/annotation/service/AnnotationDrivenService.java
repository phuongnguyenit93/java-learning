package com.example.learning.module.annotation.service;

import com.example.learning.module.annotation.TrackExecution;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class AnnotationDrivenService {

    private final AopTraceLog traceLog;

    public AnnotationDrivenService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @TrackExecution("annotation-demo")
    public String executeTrackedOperation() {
        traceLog.add("target:executeTrackedOperation");
        return "tracked-operation-result";
    }
}
