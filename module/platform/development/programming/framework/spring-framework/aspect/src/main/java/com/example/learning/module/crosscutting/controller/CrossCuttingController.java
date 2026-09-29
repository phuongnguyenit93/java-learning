package com.example.learning.module.crosscutting.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.crosscutting.service.CrossCuttingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aop/cross-cutting")
public class CrossCuttingController {

    private final CrossCuttingService crossCuttingService;
    private final AopTraceLog traceLog;

    public CrossCuttingController(
            CrossCuttingService crossCuttingService,
            AopTraceLog traceLog
    ) {
        this.crossCuttingService = crossCuttingService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/1.CrossCutting/CrossCutting.md#cross-cutting-demo
     * Purpose: Chứng minh một logging concern có thể được áp dụng cho nhiều business method mà không lặp logging trong service.
     */
    @GetMapping("/observe")
    public AopExperimentResponse observeCrossCutting() {
        traceLog.reset();

        List<String> result = List.of(
                crossCuttingService.createOrder(),
                crossCuttingService.cancelOrder()
        );

        return AopExperimentResponse.of(
                result,
                traceLog.snapshotAndClear()
        );
    }
}
