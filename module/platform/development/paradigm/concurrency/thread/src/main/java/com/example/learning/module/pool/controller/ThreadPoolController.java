package com.example.learning.module.pool.controller;

import com.example.learning.module.pool.service.ThreadPoolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/thread-pool")
public class ThreadPoolController {

    private final ThreadPoolService threadPoolService;

    public ThreadPoolController(ThreadPoolService threadPoolService) {
        this.threadPoolService = threadPoolService;
    }

    /** README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#thread-pool-flow */
    @GetMapping("/executor-flow")
    public Map<String, Object> executorFlow() throws InterruptedException {
        return threadPoolService.executorFlowDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#execute-vs-submit
     * Purpose: Phân biệt result/exception channel của execute() và submit().
     */
    @GetMapping("/execute-vs-submit")
    public Map<String, Object> executeVsSubmit() throws InterruptedException {
        return threadPoolService.executeVsSubmitDemo();
    }

    /** README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#executors-factories */
    @GetMapping("/fixed-pool-reuse")
    public Map<String, Object> fixedPoolReuse() throws InterruptedException {
        return threadPoolService.fixedPoolReuseDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#executors-factories
     * Purpose: Quan sát semantics đại diện của fixed, cached, single và scheduled executor factory bằng experiment bounded.
     */
    @GetMapping("/executor-factories")
    public Map<String, Object> executorFactories() throws InterruptedException {
        return threadPoolService.executorFactoriesDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#scheduled-execution
     * Purpose: Đo start/end và khoảng nghỉ của fixed-rate/fixed-delay khi task lâu hơn period.
     */
    @GetMapping("/scheduled-execution")
    public Map<String, Object> scheduledExecution() throws InterruptedException {
        return threadPoolService.scheduledExecutionModesDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#scheduled-failure
     * Purpose: Chứng minh exception thoát khỏi periodic task suppress các execution tiếp theo của cùng task.
     */
    @GetMapping("/scheduled-failure")
    public Map<String, Object> scheduledFailure() throws InterruptedException {
        return threadPoolService.scheduledFailureDemo();
    }

    /** README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#rejection-policy */
    @GetMapping("/caller-runs")
    public Map<String, Object> callerRuns() throws InterruptedException {
        return threadPoolService.callerRunsDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#rejection-policy
     * Purpose: So sánh semantics mất task của DiscardPolicy và DiscardOldestPolicy theo cách bounded.
     */
    @GetMapping("/discard-policies")
    public Map<String, Object> discardPolicies() throws InterruptedException {
        return threadPoolService.discardPoliciesDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#custom-rejection-handler
     * Purpose: Minh họa custom RejectedExecutionHandler ghi nhận overload rồi reject rõ ràng.
     */
    @GetMapping("/custom-rejection-handler")
    public Map<String, Object> customRejectionHandler() throws InterruptedException {
        return threadPoolService.customRejectionHandlerDemo();
    }

    /** README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#graceful-shutdown */
    @GetMapping("/graceful-shutdown")
    public Map<String, Object> gracefulShutdown() throws InterruptedException {
        return threadPoolService.gracefulShutdownDemo();
    }

    /**
     * README: readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md#graceful-shutdown
     * Purpose: Quan sát shutdownNow interrupt running task và trả lại task chưa bắt đầu.
     */
    @GetMapping("/forced-shutdown")
    public Map<String, Object> forcedShutdown() throws InterruptedException {
        return threadPoolService.forcedShutdownDemo();
    }
}
