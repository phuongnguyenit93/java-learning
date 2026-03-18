package com.example.learning.module.coordination.blockingQueue.controller;

import com.example.learning.module.coordination.blockingQueue.service.BlockingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("queue")
@RequiredArgsConstructor
public class BlockingQueueController {
    private final BlockingQueueService blockingQueueService;

    @GetMapping("blocking-queue")
    public void blockingQueueExample() throws InterruptedException {
        blockingQueueService.blockingQueueExample();
    }

    @GetMapping("synchronous-queue")
    public void synchronousQueueExample() {
        blockingQueueService.synchronousQueueExample();
    }

    @GetMapping("array-queue")
    public void arrayQueueExample() {
        blockingQueueService.arrayBlockQueue();
    }

    @GetMapping("priority-queue")
    public void priorityQueueExample() throws InterruptedException {
        blockingQueueService.priorityQueueExample();
    }
}
