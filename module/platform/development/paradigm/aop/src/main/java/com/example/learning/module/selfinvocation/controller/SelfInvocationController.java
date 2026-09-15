package com.example.learning.module.selfinvocation.controller;

import com.example.learning.module.annotation.TrackExecution;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.selfinvocation.service.SelfInvocationService;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aop/self-invocation")
public class SelfInvocationController {

    private final SelfInvocationService selfInvocationService;
    private final AopTraceLog traceLog;

    public SelfInvocationController(
            SelfInvocationService selfInvocationService,
            AopTraceLog traceLog
    ) {
        this.selfInvocationService = selfInvocationService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/9.SelfInvocation/SelfInvocation.md#self-invocation-demo
     * Purpose: So sánh this.inner() bên trong target với lời gọi inner() từ bên ngoài qua Spring proxy.
     */
    @GetMapping("/compare")
    public AopExperimentResponse compareSelfInvocation() {
        traceLog.reset();
        String selfInvocationResult = selfInvocationService.outerCallsInner();
        List<String> selfInvocationEvents = traceLog.snapshot();

        traceLog.reset();
        String proxyInvocationResult = selfInvocationService.innerTrackedMethod();
        List<String> proxyInvocationEvents = traceLog.snapshotAndClear();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selfInvocationResult", selfInvocationResult);
        result.put("selfInvocationEvents", selfInvocationEvents);
        result.put("proxyInvocationResult", proxyInvocationResult);
        result.put("proxyInvocationEvents", proxyInvocationEvents);

        return AopExperimentResponse.of(
                result,
                List.of()
        );
    }

    /**
     * README: readme/vi/menu/9.SelfInvocation/SelfInvocation.md#final-method-demo
     * Purpose: Chứng minh final method trên class-based proxy không thể bị override để advice intercept.
     */
    @GetMapping("/final-method")
    public AopExperimentResponse finalMethodLimitation() {
        traceLog.reset();
        String result = selfInvocationService.finalTrackedMethod();

        var finalTrackedMethod = Arrays.stream(SelfInvocationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("finalTrackedMethod"))
                .findFirst()
                .orElseThrow();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("isAopProxy", AopUtils.isAopProxy(selfInvocationService));
        facts.put("isCglibProxy", AopUtils.isCglibProxy(selfInvocationService));
        facts.put(
                "annotationPresentOnFinalMethod",
                finalTrackedMethod.isAnnotationPresent(TrackExecution.class)
        );
        facts.put(
                "methodIsFinal",
                java.lang.reflect.Modifier.isFinal(finalTrackedMethod.getModifiers())
        );

        return new AopExperimentResponse(
                result,
                traceLog.snapshotAndClear(),
                facts
        );
    }
}
