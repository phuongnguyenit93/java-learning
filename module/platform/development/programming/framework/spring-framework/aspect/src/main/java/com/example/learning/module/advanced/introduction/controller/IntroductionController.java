package com.example.learning.module.advanced.introduction.controller;

import com.example.learning.module.advanced.introduction.contract.UsageTracked;
import com.example.learning.module.advanced.introduction.service.IntroductionTargetService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aop/advanced/introduction")
public class IntroductionController {

    private final IntroductionTargetService introductionTargetService;
    private final AopTraceLog traceLog;

    public IntroductionController(
            IntroductionTargetService introductionTargetService,
            AopTraceLog traceLog
    ) {
        this.introductionTargetService = introductionTargetService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/14.Introduction/Introduction.md#introduction-demo
     * Purpose: Chứng minh AOP proxy có thể expose thêm interface mà target class ban đầu không implement.
     */
    @GetMapping("/observe")
    public AopExperimentResponse observeIntroduction() {
        traceLog.reset();

        boolean introduced = introductionTargetService instanceof UsageTracked;
        if (!introduced) {
            return new AopExperimentResponse(
                    "introduction-not-applied",
                    traceLog.snapshotAndClear(),
                    Map.of("introducedInterface", false)
            );
        }

        UsageTracked usageTracked = (UsageTracked) introductionTargetService;
        usageTracked.resetUseCount();
        int before = usageTracked.getUseCount();
        usageTracked.incrementUseCount();
        usageTracked.incrementUseCount();
        int after = usageTracked.getUseCount();

        String result = introductionTargetService.businessOperation();
        List<String> events = traceLog.snapshotAndClear();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("introducedInterface", true);
        facts.put("targetClassImplementsUsageTracked", UsageTracked.class.isAssignableFrom(IntroductionTargetService.class));
        facts.put("proxyImplementsUsageTracked", UsageTracked.class.isAssignableFrom(introductionTargetService.getClass()));
        facts.put("isAopProxy", AopUtils.isAopProxy(introductionTargetService));
        facts.put("useCountBefore", before);
        facts.put("useCountAfter", after);

        return new AopExperimentResponse(result, events, facts);
    }
}
