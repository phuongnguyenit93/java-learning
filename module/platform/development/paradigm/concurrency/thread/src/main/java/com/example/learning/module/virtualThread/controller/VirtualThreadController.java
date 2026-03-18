package com.example.learning.module.virtualThread.controller;

import com.example.learning.module.virtualThread.service.VirtualThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("virtual-thread")
@RequiredArgsConstructor
public class VirtualThreadController {
    private final VirtualThreadService virtualThreadService;

    @GetMapping("benchmark")
    public void virtualThreadBenchmark() {
        virtualThreadService.virtualThreadBenchmark();
    }

    @GetMapping("with-basic")
    public void virtualThreadBasic() {
        virtualThreadService.virtualThreadBasicExample();
    }

    @GetMapping("with-builder")
    public void virtualThreadBuilder() {
        virtualThreadService.virtualThreadBuilderExample();
    }

    @GetMapping("with-executor")
    public void virtualThreadExecutor() {
        virtualThreadService.virtualThreadExecutorExample();
    }
}
