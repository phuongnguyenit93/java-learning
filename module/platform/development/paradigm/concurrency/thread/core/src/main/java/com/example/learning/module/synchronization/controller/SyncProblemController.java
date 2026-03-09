package com.example.learning.module.synchronization.controller;

import com.example.learning.module.synchronization.service.SyncProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("sync/problem")
@RequiredArgsConstructor
public class SyncProblemController {
    private final SyncProblemService syncProblemService;

    @GetMapping("instruction-reorder")
    public void instructionReoder() throws InterruptedException {
        syncProblemService.instructionReordering();
    }

    @GetMapping("stale-data")
    public void staleData() throws InterruptedException {
        syncProblemService.staleData();
    }

    @GetMapping("race-condition")
    public void raceCondidtion() throws InterruptedException {
        syncProblemService.raceCondition();
    }

    @GetMapping("deadlock")
    public void deadlock() throws InterruptedException {
        syncProblemService.deadlock();
    }
}
