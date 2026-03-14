package com.example.learning.module.context.controller;

import com.example.learning.module.context.service.ThreadLocalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thread-local")
@RequiredArgsConstructor
public class ThreadLocalController {
    private final ThreadLocalService threadLocalService;

    @GetMapping("thread-local")
    public void threadLocalDemo() {
        threadLocalService.threadLocalDemo();
    }

    @GetMapping("inheritable-thread-local")
    public void inheritableThreadLocalDemo() throws InterruptedException {
        threadLocalService.inheritableThreadLocalDemo();
    }

    @GetMapping("thread-pool-issue")
    public void threadPoolIssueDemo() throws InterruptedException {
        threadLocalService.threadPoolIssueDemo();
    }
}
