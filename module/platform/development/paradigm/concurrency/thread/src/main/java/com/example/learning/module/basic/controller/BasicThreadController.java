package com.example.learning.module.basic.controller;

import com.example.learning.module.basic.service.BasicThreadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/basic")
public class BasicThreadController {

    private final BasicThreadService basicThreadService;

    public BasicThreadController(BasicThreadService basicThreadService) {
        this.basicThreadService = basicThreadService;
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#process-and-thread
     * Purpose: Quan sát JVM process và thread đang xử lý HTTP request.
     */
    @GetMapping("/process-thread")
    public Map<String, Object> processAndThread() {
        return basicThreadService.describeProcessAndCurrentThread();
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#start-vs-run
     * Purpose: So sánh gọi run() trực tiếp với start() một Thread mới.
     */
    @GetMapping("/start-vs-run")
    public List<String> startVsRun() throws InterruptedException {
        return basicThreadService.compareRunAndStart();
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#thread-state
     * Purpose: Quan sát lần lượt các giá trị trong Thread.State.
     */
    @GetMapping("/thread-life-cycle")
    public List<String> threadLifeCycle() throws InterruptedException {
        return basicThreadService.observeThreadLifecycle();
    }
}
