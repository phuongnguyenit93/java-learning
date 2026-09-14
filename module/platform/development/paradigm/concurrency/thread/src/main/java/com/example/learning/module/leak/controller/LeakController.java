package com.example.learning.module.leak.controller;

import com.example.learning.module.leak.service.LeakService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/leak")
public class LeakController {

    private final LeakService leakService;

    public LeakController(LeakService leakService) {
        this.leakService = leakService;
    }

    /** README: readme/vi/menu/10.Leak/Leak.md#thread-leak */
    @GetMapping("/thread-pattern")
    public Map<String, Object> threadLeakPattern() throws InterruptedException {
        return leakService.threadLeakPatternDemo();
    }

    /** README: readme/vi/menu/10.Leak/Leak.md#pool-leak */
    @GetMapping("/pool-pattern")
    public Map<String, Object> poolLeakPattern() throws InterruptedException {
        return leakService.poolLeakPatternDemo();
    }

    /** README: readme/vi/menu/10.Leak/Leak.md#queue-growth */
    @GetMapping("/queue-pressure")
    public Map<String, Object> queuePressure() throws InterruptedException {
        return leakService.queuePressureDemo();
    }
}
