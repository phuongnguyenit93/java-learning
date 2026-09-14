package com.example.learning.module.coordination.controller;

import com.example.learning.module.coordination.service.CoordinationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/coordination")
public class CoordinationController {

    private final CoordinationService coordinationService;

    public CoordinationController(CoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#join */
    @GetMapping("/join")
    public Map<String, Object> join() throws InterruptedException {
        return coordinationService.joinDemo();
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#wait-notify */
    @GetMapping("/wait-notify")
    public Map<String, Object> waitNotify() throws InterruptedException {
        return coordinationService.waitNotifyDemo();
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#condition */
    @GetMapping("/condition")
    public Map<String, Object> condition() throws InterruptedException {
        return coordinationService.conditionDemo();
    }

    /**
     * README: readme/vi/menu/5.Coordination/Coordination.md#lock-support
     * Purpose: Quan sát targeted unpark, blocker metadata và park() return do interrupt mà không ném InterruptedException.
     */
    @GetMapping("/lock-support")
    public Map<String, Object> lockSupport() throws InterruptedException {
        return coordinationService.lockSupportDemo();
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#producer-consumer */
    @GetMapping("/blocking-queue")
    public Map<String, Object> blockingQueue() throws InterruptedException {
        return coordinationService.blockingQueueDemo();
    }

    /**
     * README: readme/vi/menu/5.Coordination/Coordination.md#blocking-queue-variants
     * Purpose: So sánh bounded queue, linked queue, priority queue và drainTo bằng experiment hữu hạn.
     */
    @GetMapping("/blocking-queue-variants")
    public Map<String, Object> blockingQueueVariants() throws InterruptedException {
        return coordinationService.blockingQueueVariantsDemo();
    }

    /**
     * README: readme/vi/menu/5.Coordination/Coordination.md#priority-queue-semantics
     * Purpose: Minh họa equal-priority không nên được coi là FIFO contract và priority queue không có bounded backpressure.
     */
    @GetMapping("/priority-queue-semantics")
    public Map<String, Object> priorityQueueSemantics() {
        return coordinationService.priorityQueueSemanticsDemo();
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#synchronous-queue */
    @GetMapping("/synchronous-queue")
    public Map<String, Object> synchronousQueue() throws InterruptedException {
        return coordinationService.synchronousQueueDemo();
    }

    /** README: readme/vi/menu/5.Coordination/Coordination.md#synchronizers */
    @GetMapping("/synchronizers")
    public Map<String, Object> synchronizers() throws InterruptedException {
        return coordinationService.synchronizersDemo();
    }

    /**
     * README: readme/vi/menu/5.Coordination/Coordination.md#synchronizer-timeout-failure
     * Purpose: Quan sát timeout/failure semantics của Latch, Barrier, Semaphore và Phaser.
     */
    @GetMapping("/synchronizers-timeout")
    public Map<String, Object> synchronizersTimeout() throws InterruptedException {
        return coordinationService.synchronizerTimeoutDemo();
    }

    /**
     * README: readme/vi/menu/5.Coordination/Coordination.md#semaphore-permit-accounting
     * Purpose: Chứng minh Semaphore không enforce ownership và release thừa có thể làm tăng permit sai.
     */
    @GetMapping("/semaphore-permit-accounting")
    public Map<String, Object> semaphorePermitAccounting() {
        return coordinationService.semaphorePermitAccountingDemo();
    }
}
