package com.example.learning.module.pool.controller;

import com.example.learning.module.pool.service.RejectExecutionHandlerService;
import com.example.learning.module.pool.service.ThreadPoolExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thread-pool")
@RequiredArgsConstructor
public class ThreadPoolController {
    private final ThreadPoolExecutorService threadPoolExecutorService;
    private final RejectExecutionHandlerService rejectExecutionHandlerService;

    @GetMapping("/executor/fixed-thread-pool")
    public void fixedThreadPoolExample() {
        threadPoolExecutorService.fixedThreadPool();
    }

    @GetMapping("/executor/cache-thread-pool")
    public void cacheThreadPoolExample() {
        threadPoolExecutorService.cacheThreadPool();
    }

    @GetMapping("/executor/single-thread-pool")
    public void singleThreadPoolExample() {
        threadPoolExecutorService.singleThreadPool();
    }

    @GetMapping("/executor/schedule-thread-pool")
    public void scheduleThreadPoolExample() {
        threadPoolExecutorService.scheduleThreadPool();
    }

    @GetMapping("/reject/reject-abort-policy")
    public void abortPolicy() throws InterruptedException {
        rejectExecutionHandlerService.abortPolicy();
    }

    @GetMapping("/reject/caller-run-policy")
    public void callerRunPolicy() throws InterruptedException {
        rejectExecutionHandlerService.callerRunPolicy();
    }

    @GetMapping("/reject/discard-policy")
    public void discardPolicy() {
        rejectExecutionHandlerService.discardPolicy();
    }

    @GetMapping("/reject/discard-oldest-policy")
    public void discardOldestPolicy() {
        rejectExecutionHandlerService.discardOldestPolicy();
    }
}
