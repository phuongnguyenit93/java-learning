package com.example.learning.module.patterns.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.patterns.service.PracticalPatternService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aop/patterns")
public class PracticalPatternController {

    private final PracticalPatternService practicalPatternService;
    private final AopTraceLog traceLog;

    public PracticalPatternController(
            PracticalPatternService practicalPatternService,
            AopTraceLog traceLog
    ) {
        this.practicalPatternService = practicalPatternService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/10.Patterns/Patterns.md#practical-demo
     * Purpose: Ghép auditing và timing thành cross-cutting behavior mà business method không phải tự triển khai hai concern đó.
     */
    @GetMapping("/checkout")
    public AopExperimentResponse checkout(
            @RequestParam(defaultValue = "book") String item
    ) {
        traceLog.reset();
        String result = practicalPatternService.checkout(item);
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }
}
