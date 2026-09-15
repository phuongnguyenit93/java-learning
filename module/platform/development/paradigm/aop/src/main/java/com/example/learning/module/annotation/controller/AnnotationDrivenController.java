package com.example.learning.module.annotation.controller;

import com.example.learning.module.annotation.service.AnnotationDrivenService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aop/annotation")
public class AnnotationDrivenController {

    private final AnnotationDrivenService annotationDrivenService;
    private final AopTraceLog traceLog;

    public AnnotationDrivenController(
            AnnotationDrivenService annotationDrivenService,
            AopTraceLog traceLog
    ) {
        this.annotationDrivenService = annotationDrivenService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/7.Annotation/Annotation.md#annotation-demo
     * Purpose: Dùng custom annotation như declarative contract và đọc metadata của annotation trong Aspect.
     */
    @GetMapping("/track")
    public AopExperimentResponse trackByAnnotation() {
        traceLog.reset();
        String result = annotationDrivenService.executeTrackedOperation();
        return AopExperimentResponse.of(result, traceLog.snapshotAndClear());
    }
}
