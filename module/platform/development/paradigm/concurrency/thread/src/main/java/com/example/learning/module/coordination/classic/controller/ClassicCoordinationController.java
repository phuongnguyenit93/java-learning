package com.example.learning.module.coordination.classic.controller;

import com.example.learning.module.coordination.classic.service.ClassicCoordinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("classic-coordination")
@RequiredArgsConstructor
public class ClassicCoordinationController {
    private final ClassicCoordinationService classicCoordinationService;

    @GetMapping("join-thread")
    public void joinThread() throws InterruptedException {
        classicCoordinationService.joinThread();
    }

    @GetMapping("wait-and-notify-thread")
    public void waitAndNotifyThread() throws InterruptedException {
        classicCoordinationService.waitAndNotifyThread();
    }
}
