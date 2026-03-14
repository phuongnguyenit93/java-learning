package com.example.learning.module.coordination.modern.controller;

import com.example.learning.module.coordination.modern.service.LockConditionService;
import com.example.learning.module.coordination.modern.service.LockSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("modern-coordination")
@RequiredArgsConstructor
public class ModernCoordinationController {
    private final LockConditionService lockConditionService;
    private final LockSupportService lockSupportService;

    @GetMapping("lock-condition")
    public void lockConditionExample() {
        lockConditionService.conditionLockExample();
    }

    @GetMapping("lock-support")
    public void lockSupportExample() throws InterruptedException {
        lockSupportService.lockSupportExample();
    }
}
