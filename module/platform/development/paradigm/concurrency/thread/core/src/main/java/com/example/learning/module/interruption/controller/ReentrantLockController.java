package com.example.learning.module.interruption.controller;

import com.example.learning.module.interruption.service.ReentrantLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("reentrant-lock")
@RequiredArgsConstructor
public class ReentrantLockController {
    private final ReentrantLockService reentrantLockService;

    @GetMapping("example")
    public void reentrantLockExample() {
        reentrantLockService.reentrantLockSample();
    }

    @GetMapping("fair-lock")
    public void fairLockExample() throws InterruptedException {
        reentrantLockService.reentrantFairnessSample();
    }

    @GetMapping("unfair-lock")
    public void unfairLockExample() throws InterruptedException {
        reentrantLockService.reentrantUnfairnessSample();
    }
    @GetMapping("interruptible-lock")
    public void interruptibleLock() throws InterruptedException {
        reentrantLockService.interruptibleLockExample();
    }
}
