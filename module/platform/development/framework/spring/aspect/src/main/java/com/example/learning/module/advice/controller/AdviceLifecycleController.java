package com.example.learning.module.advice.controller;

import com.example.learning.module.advice.service.AdviceLifecycleService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aop/advice")
public class AdviceLifecycleController {

    private final AdviceLifecycleService adviceLifecycleService;
    private final AopTraceLog traceLog;

    public AdviceLifecycleController(
            AdviceLifecycleService adviceLifecycleService,
            AopTraceLog traceLog
    ) {
        this.adviceLifecycleService = adviceLifecycleService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/5.Advice/Advice.md#advice-success-demo
     * Purpose: Quan sát @Before, target, @AfterReturning và @After khi target return bình thường.
     */
    @GetMapping("/success")
    public AopExperimentResponse success() {
        traceLog.reset();

        String result = adviceLifecycleService.success();

        return AopExperimentResponse.of(
                result,
                traceLog.snapshotAndClear()
        );
    }

    /**
     * README: readme/vi/menu/5.Advice/Advice.md#advice-failure-demo
     * Purpose: Quan sát @AfterThrowing và @After khi target thoát ra bằng exception.
     */
    @GetMapping("/failure")
    public AopExperimentResponse failure() {
        traceLog.reset();

        String result;

        try {
            result = adviceLifecycleService.failure();
        } catch (IllegalStateException exception) {
            result = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }

        return AopExperimentResponse.of(
                result,
                traceLog.snapshotAndClear()
        );
    }
}
