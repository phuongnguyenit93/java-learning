package com.example.learning.module.context.controller;

import com.example.learning.module.context.service.ThreadLocalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/context")
public class ThreadLocalController {

    private final ThreadLocalService threadLocalService;

    public ThreadLocalController(ThreadLocalService threadLocalService) {
        this.threadLocalService = threadLocalService;
    }

    /** README: readme/vi/menu/8.Context/ThreadLocal.md#thread-local-isolation */
    @GetMapping("/isolation")
    public Map<String, Object> isolation() throws InterruptedException {
        return threadLocalService.isolationDemo();
    }

    /** README: readme/vi/menu/8.Context/ThreadLocal.md#inheritable-thread-local */
    @GetMapping("/inheritance")
    public Map<String, Object> inheritance() throws InterruptedException {
        return threadLocalService.inheritanceDemo();
    }

    /** README: readme/vi/menu/8.Context/ThreadLocal.md#thread-pool-problem */
    @GetMapping("/pool-reuse-problem")
    public Map<String, Object> poolReuseProblem() throws Exception {
        return threadLocalService.poolReuseProblemDemo();
    }

    /** README: readme/vi/menu/8.Context/ThreadLocal.md#context-propagation */
    @GetMapping("/explicit-propagation")
    public Map<String, Object> explicitPropagation() throws Exception {
        return threadLocalService.explicitPropagationDemo();
    }
}
