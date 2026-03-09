package com.example.learning.module.interruption.controller;

import com.example.learning.module.interruption.service.InterruptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("interruption")
@RequiredArgsConstructor
public class InterruptionController {
    private final InterruptionService interruptionService;

    @GetMapping("worker")
    public void workerInterruption() throws InterruptedException {
        interruptionService.workerInterruption();
    }

    @GetMapping("blocker")
    public void blockerInterruption() throws InterruptedException {
        interruptionService.blockingInterruption();

    }

    @GetMapping("shutdown-by-java")
    public void gracefulShutdownByJava() throws InterruptedException {
        interruptionService.gracefulShutdownDemoByJava();
    }

    @GetMapping("shutdown-by-spring")
    public void gracefulShutdownBySpring() throws InterruptedException {
        interruptionService.gracefulShutdownDemoBySpring();
    }


    @GetMapping("interrupted-demo")
    public void interruptedDemo() throws InterruptedException {
        interruptionService.interruptedDemo();
    }
}
