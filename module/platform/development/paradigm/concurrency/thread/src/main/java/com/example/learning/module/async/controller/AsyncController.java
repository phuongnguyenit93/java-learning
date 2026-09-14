package com.example.learning.module.async.controller;

import com.example.learning.module.async.service.AsyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/async")
public class AsyncController {

    private final AsyncService asyncService;

    public AsyncController(AsyncService asyncService) {
        this.asyncService = asyncService;
    }

    /** README: readme/vi/menu/7.Async/Async.md#future */
    @GetMapping("/future")
    public Map<String, Object> future() throws Exception {
        return asyncService.futureDemo();
    }

    /**
     * README: readme/vi/menu/7.Async/Async.md#future-cancellation
     * Purpose: So sánh cancel(false) và cancel(true) trên task đang chạy và liên hệ cancellation với interruption.
     */
    @GetMapping("/future-cancellation")
    public Map<String, Object> futureCancellation() throws InterruptedException {
        return asyncService.futureCancellationDemo();
    }

    /** README: readme/vi/menu/7.Async/Async.md#completable-future-pipeline */
    @GetMapping("/pipeline")
    public CompletableFuture<Map<String, Object>> pipeline() {
        return asyncService.pipelineDemo();
    }

    /** README: readme/vi/menu/7.Async/Async.md#combine-race */
    @GetMapping("/fastest")
    public CompletableFuture<Map<String, Object>> fastest() {
        return asyncService.fastestCompletionDemo();
    }

    /** README: readme/vi/menu/7.Async/Async.md#exception-handling */
    @GetMapping("/exception")
    public CompletableFuture<Map<String, Object>> exceptionHandling() {
        return asyncService.exceptionHandlingDemo();
    }

    /**
     * README: readme/vi/menu/7.Async/Async.md#async-variant
     * Purpose: Quan sát execution policy của thenApply và thenApplyAsync với executor chỉ định rõ.
     */
    @GetMapping("/execution-variant")
    public Map<String, Object> executionVariant() throws Exception {
        return asyncService.executionVariantDemo();
    }

    /**
     * README: readme/vi/menu/7.Async/Async.md#combine-race
     * Purpose: Chứng minh allOf đợi mọi input complete, có thể complete exceptionally và không tự cancel sibling.
     */
    @GetMapping("/all-of")
    public Map<String, Object> allOf() throws Exception {
        return asyncService.allOfDemo();
    }

    /**
     * README: readme/vi/menu/7.Async/Async.md#timeout-cancellation
     * Purpose: Chứng minh timeout của CompletableFuture không mặc định interrupt/kill underlying work.
     */
    @GetMapping("/timeout-cancellation")
    public Map<String, Object> timeoutCancellation() throws InterruptedException {
        return asyncService.timeoutCancellationDemo();
    }
}
