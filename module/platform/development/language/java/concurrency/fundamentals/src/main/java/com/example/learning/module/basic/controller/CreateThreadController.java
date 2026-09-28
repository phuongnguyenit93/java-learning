package com.example.learning.module.basic.controller;

import com.example.learning.module.basic.service.CreateThreadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/create")
public class CreateThreadController {

    private final CreateThreadService createThreadService;

    public CreateThreadController(CreateThreadService createThreadService) {
        this.createThreadService = createThreadService;
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#create-thread
     * Purpose: Minh họa cách gắn task trực tiếp vào subclass của Thread.
     */
    @GetMapping("/extends")
    public List<String> createByExtends() throws InterruptedException {
        return createThreadService.createByExtends();
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#create-thread
     * Purpose: Minh họa việc tách Runnable task khỏi Thread thực thi task đó.
     */
    @GetMapping("/runnable")
    public List<String> createByRunnable() throws InterruptedException {
        return createThreadService.createByRunnable();
    }

    /**
     * README: readme/vi/menu/1.Basic/Basic.md#create-thread
     * Purpose: Minh họa Callable được FutureTask bọc rồi chạy bởi Thread.
     */
    @GetMapping("/callable")
    public List<String> createByCallable()
            throws ExecutionException, InterruptedException {
        return createThreadService.createByCallable();
    }
}
