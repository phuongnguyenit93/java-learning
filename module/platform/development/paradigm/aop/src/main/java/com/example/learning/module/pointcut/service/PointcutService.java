package com.example.learning.module.pointcut.service;

import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.pointcut.annotation.PointcutMarker;
import org.springframework.stereotype.Service;

@Service
public class PointcutService {

    private final AopTraceLog traceLog;

    public PointcutService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String byExecution() {
        traceLog.add("target:byExecution");
        return "execution-result";
    }

    @PointcutMarker
    public String byAnnotation() {
        traceLog.add("target:byAnnotation");
        return "annotation-result";
    }

    public String byArgs(String value) {
        traceLog.add("target:byArgs:" + value);
        return "args-result:" + value;
    }

    public String byRuntimeArgs(Object value) {
        traceLog.add("target:byRuntimeArgs:" + value + ":declaredType=Object");
        return "runtime-args-result:" + value;
    }

    public String byWithin() {
        traceLog.add("target:byWithin");
        return "within-result";
    }

    public String byThisAndTarget() {
        traceLog.add("target:byThisAndTarget");
        return "this-target-result";
    }

    public String byBean() {
        traceLog.add("target:byBean");
        return "bean-result";
    }

    public String unmatched() {
        traceLog.add("target:unmatched");
        return "unmatched-result";
    }
}
