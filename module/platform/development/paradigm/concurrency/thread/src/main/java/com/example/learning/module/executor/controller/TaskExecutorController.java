package com.example.learning.module.executor.controller;

import com.example.learning.module.executor.service.TaskExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("spring-executor")
@RequiredArgsConstructor
public class TaskExecutorController {
    private final TaskExecutorService taskExecutorService;

    @GetMapping("example")
    public String springExecutorExample() throws InterruptedException {
        taskExecutorService.asyncTaskExecutor();
        return "Task đang thực hiện ngầm , có thể thực hiện việc khác rồi";
    }
}
