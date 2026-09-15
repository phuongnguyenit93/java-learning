package com.example.learning.module.pointcut.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.pointcut.service.PointcutService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aop/pointcut")
public class PointcutController {

    private final PointcutService pointcutService;
    private final AopTraceLog traceLog;

    public PointcutController(
            PointcutService pointcutService,
            AopTraceLog traceLog
    ) {
        this.pointcutService = pointcutService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/4.Pointcut/Pointcut.md#pointcut-demo
     * Purpose: So sánh execution, annotation, args runtime type, within, named composition, this/target, bean() và một method không match.
     */
    @GetMapping("/compare")
    public AopExperimentResponse comparePointcuts() {
        traceLog.reset();

        List<String> result = List.of(
                pointcutService.byExecution(),
                pointcutService.byAnnotation(),
                pointcutService.byArgs("demo"),
                pointcutService.byRuntimeArgs("runtime-string"),
                pointcutService.byWithin(),
                pointcutService.byThisAndTarget(),
                pointcutService.byBean(),
                pointcutService.unmatched()
        );

        return AopExperimentResponse.of(
                result,
                traceLog.snapshotAndClear()
        );
    }
}
