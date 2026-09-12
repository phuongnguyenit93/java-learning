package com.example.learning.module.leak.controller;

import com.example.learning.module.leak.service.LeakService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("leak")
public class LeakController {
    private final LeakService leakService;

    @GetMapping("/thread")
    public String threadLeak() {
        return leakService.createThreadLeak();
    }

    @GetMapping("/pool")
    public String poolLeak() {
        return leakService.createPoolLeak();
    }
}
