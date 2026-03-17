package com.example.learning.module.synchronization.concurrent.controller;

import com.example.learning.module.synchronization.concurrent.service.ConcurrentHashMapService;
import com.example.learning.module.synchronization.concurrent.service.ConcurrentLinkedQueueService;
import com.example.learning.module.synchronization.concurrent.service.ConcurrentSkipListService;
import com.example.learning.module.synchronization.concurrent.service.CopyOnWriteArrayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("concurrent-collection")
@RequiredArgsConstructor
public class ConcurrentCollectionController {
    private final ConcurrentHashMapService concurrentHashMapService;
    private final ConcurrentSkipListService concurrentSkipListService;
    private final CopyOnWriteArrayService copyOnWriteArrayService;
    private final ConcurrentLinkedQueueService concurrentLinkedQueueService;

    @GetMapping("concurrent-hash-map")
    public void concurrentHashMap() throws InterruptedException {
        concurrentHashMapService.concurrentHashMapExample();
    }

    @GetMapping("concurrent-skip-list")
    public void concurrentSkipList() throws InterruptedException {
        concurrentSkipListService.concurrentSkipListExample();
    }

    @GetMapping("copy-on-write-array-writing")
    public void copyOnWriteArrayWhenWriting() throws InterruptedException {
        copyOnWriteArrayService.copyOnWriteArrayWhenWritingExample();
    }

    @GetMapping("copy-on-write-array-reading")
    public void copyOnWriteArrayWhenReading() throws InterruptedException {
        copyOnWriteArrayService.copyOnWriteArrayWhenReadingExample();
    }

    @GetMapping("concurrent-linked-queue")
    public void concurrentLinkedQueue() throws InterruptedException {
        concurrentLinkedQueueService.concurrentLinkedQueueExample();
    }
}
