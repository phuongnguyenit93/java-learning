package com.example.learning.module.advanced.introduction.aspect;

import com.example.learning.module.advanced.introduction.contract.DefaultUsageTracked;
import com.example.learning.module.advanced.introduction.contract.UsageTracked;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class IntroductionAspect {

    @DeclareParents(
            value = "com.example.learning.module.advanced.introduction.service.IntroductionTargetService",
            defaultImpl = DefaultUsageTracked.class
    )
    public static UsageTracked usageTracked;
}
