package com.example.learning.module.synchronization.lock.controller;

import com.example.learning.module.synchronization.lock.service.StampedLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("stamped-lock")
@RequiredArgsConstructor
public class StampedLockController {
    private final StampedLockService stampedLockService;

    @GetMapping("example")
    public void stampedExample() {
        stampedLockService.stampLockSample();
    }
}
