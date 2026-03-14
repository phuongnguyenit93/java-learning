package com.example.learning.module.coordination.async.controller;

import com.example.learning.module.coordination.async.service.AsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("async")
@RequiredArgsConstructor
public class AsyncController {
    private final AsyncService asyncService;

    @GetMapping("future")
    public void futureExample() throws ExecutionException, InterruptedException {
        asyncService.futureExample();
    }

    @GetMapping("completable-future")
    public CompletableFuture<Double> completableFutureExample() {
        return asyncService.completableFutureExample()
                .thenApply(finalPrice -> {
                    // Bước 6: In hóa đơn (Thay vì thenAccept, ta dùng thenApply để truyền dữ liệu đi tiếp)
                    System.out.println("===> 5. HÓA ĐƠN CUỐI CÙNG: " + finalPrice + " VND");
                    return finalPrice;
                })
                .whenComplete((result, ex) -> {
                    // Bước 7: Dọn dẹp (Tương đương thenRun nhưng an toàn hơn vì xử lý được cả khi có lỗi)
                    System.out.println("[System] 6. Giải phóng tài nguyên.");
                });
    }

    @GetMapping("completable-future-with-handle")
    public void completableFutureWithHandleExample() {
        asyncService.completableFutureWithAnyOfAndHandle();
    }
}
