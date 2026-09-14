package com.example.learning.module.interruption.controller;

import com.example.learning.module.interruption.service.InterruptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/interruption")
public class InterruptionController {

    private final InterruptionService interruptionService;

    public InterruptionController(InterruptionService interruptionService) {
        this.interruptionService = interruptionService;
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupt-mental-model
     * Purpose: Chứng minh interrupt() là cooperative cancellation signal cho CPU loop.
     */
    @GetMapping("/busy-loop")
    public Map<String, Object> interruptBusyWorker() throws InterruptedException {
        return interruptionService.interruptBusyWorker();
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupt-flag
     * Purpose: Phân biệt isInterrupted() với Thread.interrupted().
     */
    @GetMapping("/flag")
    public Map<String, Object> inspectInterruptFlag() throws InterruptedException {
        return interruptionService.inspectInterruptFlag();
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupted-exception
     * Purpose: Quan sát interrupt một Thread đang sleep và restore interrupt status.
     */
    @GetMapping("/sleep")
    public Map<String, Object> interruptSleepingWorker() throws InterruptedException {
        return interruptionService.interruptSleepingWorker();
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupt-cleanup
     * Purpose: Chứng minh cancellation path vẫn cleanup resource trong finally.
     */
    @GetMapping("/cleanup")
    public Map<String, Object> cancelWithCleanup() throws InterruptedException {
        return interruptionService.cancelWithCleanup();
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupt-limitations
     * Purpose: Chứng minh Thread chờ synchronized monitor không thoát khỏi BLOCKED chỉ nhờ interrupt().
     */
    @GetMapping("/synchronized-blocked")
    public Map<String, Object> interruptSynchronizedWaiter() throws InterruptedException {
        return interruptionService.interruptSynchronizedWaiter();
    }

    /**
     * README: readme/vi/menu/2.Interruption/Interruption.md#interrupt-vs-stop-flag
     * Purpose: So sánh cooperative stop flag với interrupt khi worker đang ở blocking operation.
     */
    @GetMapping("/stop-flag-vs-interrupt")
    public Map<String, Object> stopFlagVsInterrupt() throws InterruptedException {
        return interruptionService.stopFlagVsInterrupt();
    }
}
