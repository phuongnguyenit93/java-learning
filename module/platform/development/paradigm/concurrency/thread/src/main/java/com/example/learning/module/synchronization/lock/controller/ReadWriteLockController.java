package com.example.learning.module.synchronization.lock.controller;

import com.example.learning.module.synchronization.lock.service.ReadWriteLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("read-write-lock")
@RequiredArgsConstructor
public class ReadWriteLockController {
    private final ReadWriteLockService readWriteLockService;

    @GetMapping("read-read")
    public void readAndRead() throws InterruptedException {
        readWriteLockService.readAndReadLock();
    }

    @GetMapping("read-write")
    public void readAndWrite() throws InterruptedException {
        readWriteLockService.readAndWriteLock();
    }

    @GetMapping("write-write")
    public void writeAndWrite() {
        readWriteLockService.writeAndWriteLock();
    }

    @GetMapping("downgrade")
    public void downgradeKey() throws InterruptedException {
        readWriteLockService.downgradeLock();
    }
}
