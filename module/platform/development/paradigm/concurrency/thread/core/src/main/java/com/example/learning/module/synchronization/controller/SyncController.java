package com.example.learning.module.synchronization.controller;

import com.example.learning.module.synchronization.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("sync")
@RequiredArgsConstructor
public class SyncController {
    private final SyncService syncService;

    @GetMapping("run-sync-test")
    public void runSyncTest() throws InterruptedException {
        syncService.executeSyncTask();
    }
}
