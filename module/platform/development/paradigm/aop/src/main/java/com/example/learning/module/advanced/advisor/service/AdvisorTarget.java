package com.example.learning.module.advanced.advisor.service;

import com.example.learning.module.common.AopTraceLog;

public class AdvisorTarget {

    private final AopTraceLog traceLog;

    public AdvisorTarget(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public String read() {
        traceLog.add("target:read");
        return "read-result";
    }

    public String write() {
        traceLog.add("target:write");
        return "write-result";
    }

    public String writeWithMode(String mode) {
        traceLog.add("target:writeWithMode:mode=" + mode);
        return "write-with-mode-result:" + mode;
    }
}
