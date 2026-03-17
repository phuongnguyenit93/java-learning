package com.example.learning.module.forkjoin.controller;

import com.example.learning.module.forkjoin.service.ForkJoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("fork-join")
@RequiredArgsConstructor
public class ForkJoinController {
    private final ForkJoinService forkJoinService;

    @GetMapping("example")
    public void forkJoinExample() {
        forkJoinService.forkJoinExample();
    }
}
