package com.example.learning.module.basic.controller;

import com.example.learning.config.SwaggerYoutubeVideo;
import com.example.learning.module.basic.service.BasicThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("basic")
@RequiredArgsConstructor
public class BasicThreadController {
    private final BasicThreadService threadService;

    @GetMapping("/example-thread")
    @SwaggerYoutubeVideo(title = "Testing",videoId = "nd2ULXyBaV8")
    public String testThread() {
        System.out.println("1. Controller nhận được request");
        threadService.executeTaskWithThread();
        return "Yêu cầu của bạn đã được tiếp nhận và xử lý ngầm!";
    }

    @GetMapping("/example-non-thread")
    public String testNonThread() {
        System.out.println("1. Controller nhận được request");

        threadService.executeTaskWithNonThread();
        return "Yêu cầu của bạn dã được xử lý xong";
    }

    @GetMapping("/thread-life-cycle")
    public void threadLifeCycle() throws InterruptedException {
        threadService.executeThreadLifeCycle();
    }


}
