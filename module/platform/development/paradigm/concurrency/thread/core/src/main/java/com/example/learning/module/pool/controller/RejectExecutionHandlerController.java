package com.example.learning.module.pool.controller;

import com.example.learning.module.pool.service.RejectExecutionHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reject-handler")
@RequiredArgsConstructor
public class RejectExecutionHandlerController {
    private final RejectExecutionHandlerService rejectExecutionHandlerService;

    @GetMapping("abort-policy")
    public void abortPolicy() throws InterruptedException {
        rejectExecutionHandlerService.abortPolicy();
    }

    @GetMapping("caller-run-policy")
    public void callerRunPolicy() throws InterruptedException {
        rejectExecutionHandlerService.callerRunPolicy();
    }

    @GetMapping("discard-policy")
    public void discardPolicy() {
        rejectExecutionHandlerService.discardPolicy();
    }

    @GetMapping("discard-oldest-policy")
    public void discardOldestPolicy() {
        rejectExecutionHandlerService.discardOldestPolicy();
    }
}
