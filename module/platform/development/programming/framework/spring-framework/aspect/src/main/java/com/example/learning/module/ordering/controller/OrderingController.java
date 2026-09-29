package com.example.learning.module.ordering.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.ordering.service.OrderingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aop/ordering")
public class OrderingController {

    private final OrderingService orderingService;
    private final AopTraceLog traceLog;

    public OrderingController(
            OrderingService orderingService,
            AopTraceLog traceLog
    ) {
        this.orderingService = orderingService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/8.Ordering/Ordering.md#ordering-demo
     * Purpose: Quan sát hai @Around Aspect tạo call stack nested theo @Order.
     */
    @GetMapping("/observe")
    public AopExperimentResponse observeOrdering() {
        traceLog.reset();
        String result = orderingService.execute();
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }
}
