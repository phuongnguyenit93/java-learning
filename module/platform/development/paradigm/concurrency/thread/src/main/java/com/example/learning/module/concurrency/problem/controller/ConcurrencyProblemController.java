package com.example.learning.module.concurrency.problem.controller;

import com.example.learning.module.concurrency.problem.service.ConcurrencyProblemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/concurrency/problem")
public class ConcurrencyProblemController {

    private final ConcurrencyProblemService concurrencyProblemService;

    public ConcurrencyProblemController(ConcurrencyProblemService concurrencyProblemService) {
        this.concurrencyProblemService = concurrencyProblemService;
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#shared-state-race-condition
     * Purpose: Tạo lost update deterministic để quan sát race condition và atomicity problem.
     */
    @GetMapping("/race-condition")
    public Map<String, Object> raceCondition() throws InterruptedException {
        return concurrencyProblemService.deterministicRaceCondition();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#happens-before
     * Purpose: Quan sát memory-consistency guarantee của Thread.start() và Thread.join().
     */
    @GetMapping("/happens-before-start-join")
    public Map<String, Object> startJoinHappensBefore() throws InterruptedException {
        return concurrencyProblemService.startJoinHappensBefore();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#volatile-publication
     * Purpose: Publish plain data từ writer sang reader thông qua volatile flag.
     */
    @GetMapping("/volatile-publication")
    public Map<String, Object> volatilePublication() throws InterruptedException {
        return concurrencyProblemService.volatilePublication();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#volatile-publication
     * Purpose: Chứng minh volatile không biến read-modify-write thành atomic operation.
     */
    @GetMapping("/volatile-not-atomic")
    public Map<String, Object> volatileIsNotAtomic() throws InterruptedException {
        return concurrencyProblemService.volatileIsNotAtomic();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#progress-problems
     * Purpose: Tạo circular lock dependency nhưng dùng tryLock để tránh permanent deadlock.
     */
    @GetMapping("/deadlock-risk")
    public Map<String, Object> deadlockRisk() throws InterruptedException {
        return concurrencyProblemService.deadlockRisk();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#deadlock-prevention
     * Purpose: Chứng minh global lock ordering phá circular-wait condition và cho phép cả hai worker hoàn thành.
     */
    @GetMapping("/deadlock-prevention")
    public Map<String, Object> deadlockPrevention() throws InterruptedException {
        return concurrencyProblemService.deadlockPrevention();
    }

    /**
     * README: readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md#progress-problems
     * Purpose: Minh họa livelock bounded: worker vẫn hoạt động nhưng không tạo progress.
     */
    @GetMapping("/livelock")
    public Map<String, Object> boundedLivelock() throws InterruptedException {
        return concurrencyProblemService.boundedLivelock();
    }
}
