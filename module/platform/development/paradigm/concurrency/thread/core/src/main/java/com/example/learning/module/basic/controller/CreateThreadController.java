package com.example.learning.module.basic.controller;

import com.example.learning.module.basic.service.CreateThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("create")
@RequiredArgsConstructor
public class CreateThreadController {

    private final CreateThreadService createThreadService;

    @GetMapping("/extend")
    public void createByExtend() {
        createThreadService.createByExtends();
    }

    @GetMapping("/runnable")
    public void createByRunnable() {
        createThreadService.createByRunnable();
    }

    @GetMapping("/callable")
    public String createByCallable() throws ExecutionException, InterruptedException {
        createThreadService.createByCallable();
        return "Đã kích hoạt Runnable Threads (Xem console)";
    }

    @GetMapping("/pool")
    public String createByThreadPool() {
        createThreadService.createByThreadPool();
        return "Đã đẩy 10 tasks vào Thread Pool (Xem console)";
    }
}
