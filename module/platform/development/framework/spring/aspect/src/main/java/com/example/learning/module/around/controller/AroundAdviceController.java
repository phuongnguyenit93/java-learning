package com.example.learning.module.around.controller;

import com.example.learning.module.around.service.AroundAdviceService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aop/around")
public class AroundAdviceController {

    private final AroundAdviceService aroundAdviceService;
    private final AopTraceLog traceLog;

    public AroundAdviceController(
            AroundAdviceService aroundAdviceService,
            AopTraceLog traceLog
    ) {
        this.aroundAdviceService = aroundAdviceService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/6.Around/Around.md#around-timing-demo
     * Purpose: Quan sát code trước/sau proceed() và đo thời gian target execution.
     */
    @GetMapping("/timing")
    public AopExperimentResponse timing() {
        traceLog.reset();
        String result = aroundAdviceService.timedOperation();
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }

    /**
     * README: readme/vi/menu/6.Around/Around.md#around-transform-demo
     * Purpose: Chứng minh @Around có thể thay đổi return value sau khi target đã chạy.
     */
    @GetMapping("/transform")
    public AopExperimentResponse transform() {
        traceLog.reset();
        String result = aroundAdviceService.transformResult();
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }

    /**
     * README: readme/vi/menu/6.Around/Around.md#around-skip-demo
     * Purpose: Chứng minh không gọi proceed() thì target method không được thực thi.
     */
    @GetMapping("/skip")
    public AopExperimentResponse skip() {
        traceLog.reset();
        String result = aroundAdviceService.skippedTarget();
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }

    /**
     * README: readme/vi/menu/6.Around/Around.md#around-arguments-demo
     * Purpose: Chứng minh @Around có thể đọc và thay đổi arguments trước khi proceed(Object[]).
     */
    @GetMapping("/arguments")
    public AopExperimentResponse arguments() {
        traceLog.reset();
        String result = aroundAdviceService.normalizeArgument("  Book  ");
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }

    /**
     * README: readme/vi/menu/6.Around/Around.md#around-exception-demo
     * Purpose: Quan sát @Around bắt được exception nhưng rethrow để giữ nguyên target contract.
     */
    @GetMapping("/exception")
    public AopExperimentResponse exceptionPropagation() {
        traceLog.reset();

        String result;
        try {
            result = aroundAdviceService.throwsFailure();
        } catch (IllegalStateException exception) {
            result = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }

        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }
}
