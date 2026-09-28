package com.example.learning.module.terminology.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.terminology.service.TerminologyService;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/aop/terminology")
public class TerminologyController {

    private final TerminologyService terminologyService;
    private final AopTraceLog traceLog;

    public TerminologyController(
            TerminologyService terminologyService,
            AopTraceLog traceLog
    ) {
        this.terminologyService = terminologyService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/2.Terminology/Terminology.md#terminology-demo
     * Purpose: Gắn các thuật ngữ Aspect, Advice, Join Point, Target và Proxy vào một method call thật.
     */
    @GetMapping("/inspect")
    public AopExperimentResponse inspectTerminology() {
        traceLog.reset();

        String result = terminologyService.execute();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("injectedObjectClass", terminologyService.getClass().getName());
        facts.put("isAopProxy", AopUtils.isAopProxy(terminologyService));
        facts.put("targetClass", AopUtils.getTargetClass(terminologyService).getName());

        return new AopExperimentResponse(
                result,
                traceLog.snapshotAndClear(),
                facts
        );
    }
}
