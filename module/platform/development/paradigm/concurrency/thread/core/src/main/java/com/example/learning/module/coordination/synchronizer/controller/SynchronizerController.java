package com.example.learning.module.coordination.synchronizer.controller;

import com.example.learning.module.coordination.synchronizer.service.SynchronizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("synchronizer")
@RequiredArgsConstructor
public class SynchronizerController {
    private final SynchronizerService synchronizerService;

    @GetMapping("count-down-latch")
    public void countDownLatchExample() throws InterruptedException {
        synchronizerService.countDownLatchSample();
    }

    @GetMapping("count-down-latch-timeout")
    public void countDownLatchTimeOutExample() throws InterruptedException {
        synchronizerService.countDownLatchTimeOut();
    }

    @GetMapping("cyclic-barrier")
    public void cyclicBarrierExample() throws InterruptedException {
        synchronizerService.cyclicBarrierDemo(20);
    }

    @GetMapping("cyclic-barrier-time-out")
    public void cyclicBarrierTimeOutExample() throws InterruptedException {
        synchronizerService.cyclicBarrierDemo(1);
    }

    @GetMapping("cyclic-barrier-reset")
    public void cyclicBarrierResetExample() throws InterruptedException {
        synchronizerService.cyclicBarrierReset();
    }

    @GetMapping("semaphore")
    public void semaphoreExample() {
        synchronizerService.semaphoreExample();
    }

    @GetMapping("phaser")
    public void phaserExample() {
        synchronizerService.phaserExample();
    }

}
