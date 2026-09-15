package com.example.learning.module.around.service;

import com.example.learning.module.common.AopTraceLog;
import org.springframework.stereotype.Service;

@Service
public class AroundAdviceService {

    private final AopTraceLog traceLog;

    public AroundAdviceService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String timedOperation() {
        traceLog.add("target:timedOperation");
        return "timed-target-result";
    }

    public String transformResult() {
        traceLog.add("target:transformResult");
        return "original-result";
    }

    public String skippedTarget() {
        traceLog.add("target:skippedTarget");
        return "this-result-should-not-be-seen";
    }

    public String normalizeArgument(String item) {
        traceLog.add("target:normalizeArgument:item=" + item);
        return item;
    }

    public String throwsFailure() {
        traceLog.add("target:throwsFailure");
        throw new IllegalStateException("intentional-around-demo-error");
    }
}
