package com.example.learning.module.basic.controller;

import com.example.learning.module.basic.service.DaemonThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("daemon")
@RequiredArgsConstructor
public class DaemonThreadController {
    private final DaemonThreadService daemonThreadService;

    @GetMapping("/daemon-thread")
    public void threadDaemon() {
        daemonThreadService.executeDaemonThread();
    }

    @GetMapping("/stop-thread")
    public String stopDaemon() {
        daemonThreadService.stopDaemon();

        return "Daemon đã stop";
    }

    @GetMapping("/restart-thread")
    public String restartDaemon() {
        daemonThreadService.restartDaemon();

        return "Daemon đã stop";
    }
}
