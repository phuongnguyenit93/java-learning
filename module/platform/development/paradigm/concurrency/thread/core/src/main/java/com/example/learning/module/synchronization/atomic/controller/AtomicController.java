package com.example.learning.module.synchronization.atomic.controller;

import com.example.learning.module.synchronization.atomic.service.AccumulatorService;
import com.example.learning.module.synchronization.atomic.service.AdderService;
import com.example.learning.module.synchronization.atomic.service.AtomicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("atomic")
@RequiredArgsConstructor
public class AtomicController {
    private final AdderService adderService;
    private final AtomicService atomicService;
    private final AccumulatorService accumulatorService;

    @GetMapping("atomic-benchmark")
    public void atomicBenchmark() throws InterruptedException {
        atomicService.atomicBenchMark();
    }

    @GetMapping("adder-benchmark")
    public void adderBenchmark() throws InterruptedException {
        adderService.longAdderBenchmark();
    }

    @GetMapping("accumulator-example")
    public void accumulatorExample() throws InterruptedException {
        accumulatorService.accumulatorExample();
    }
}
