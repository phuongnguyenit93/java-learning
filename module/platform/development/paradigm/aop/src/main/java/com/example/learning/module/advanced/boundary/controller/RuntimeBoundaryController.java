package com.example.learning.module.advanced.boundary.controller;

import com.example.learning.module.advanced.boundary.aspect.RuntimeBoundaryAspect;
import com.example.learning.module.advanced.boundary.service.RuntimeBoundaryService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aop/advanced/runtime-boundary")
public class RuntimeBoundaryController {

    private final RuntimeBoundaryService runtimeBoundaryService;
    private final AopTraceLog traceLog;
    private final ApplicationContext applicationContext;

    public RuntimeBoundaryController(
            RuntimeBoundaryService runtimeBoundaryService,
            AopTraceLog traceLog,
            ApplicationContext applicationContext
    ) {
        this.runtimeBoundaryService = runtimeBoundaryService;
        this.traceLog = traceLog;
        this.applicationContext = applicationContext;
    }

    /**
     * README: readme/vi/menu/15.RuntimeBoundary/RuntimeBoundary.md#runtime-boundary-demo
     * Purpose: Phân biệt @AspectJ declaration style với runtime proxy-based Spring AOP đang dùng trong module.
     */
    @GetMapping("/inspect")
    public AopExperimentResponse inspectRuntimeBoundary() {
        traceLog.reset();
        String result = runtimeBoundaryService.execute();
        List<String> events = traceLog.snapshotAndClear();

        RuntimeBoundaryAspect aspectBean =
                applicationContext.getBean(RuntimeBoundaryAspect.class);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("aspectIsSpringBean", aspectBean != null);
        facts.put("aspectBeanClass", aspectBean.getClass().getName());
        facts.put("targetIsAopProxy", AopUtils.isAopProxy(runtimeBoundaryService));
        facts.put("targetRuntimeClass", runtimeBoundaryService.getClass().getName());
        facts.put("targetClass", AopUtils.getTargetClass(runtimeBoundaryService).getName());

        return new AopExperimentResponse(result, events, facts);
    }
}
