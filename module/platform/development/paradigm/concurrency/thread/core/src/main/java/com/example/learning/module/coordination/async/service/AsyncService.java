package com.example.learning.module.coordination.async.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class AsyncService {

    @Qualifier("completableFutureExecutor")
    private final Executor executor;

    public void futureExample() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        System.out.println("[Main] Bắt đầu đặt hàng...");

        // Callable: Công việc có trả về kết quả
        Callable<String> paymentTask = () -> {
            Thread.sleep(2000); // Giả lập thanh toán
            return "THANH TOÁN THÀNH CÔNG";
        };

        // Gửi đi và nhận lại "biên lai" Future
        Future<String> futureResult = executor.submit(paymentTask);

        // Trong lúc đợi, luồng Main có thể làm việc khác
        System.out.println("[Main] Đang chuẩn bị đóng gói hàng trong lúc đợi thanh toán...");

        // Phối hợp: Main buộc phải dừng lại ở đây để đợi kết quả thanh toán mới gửi Email được
        String result = futureResult.get(); // Chặn luồng Main tại đây (Blocking)

        System.out.println("[Main] Kết quả: " + result);
        System.out.println("[Main] Đang gửi Email xác nhận...");

        executor.shutdown();
    }

    public CompletableFuture<Double> completableFutureExample() {
        System.out.println("[Main] Bắt đầu xử lý đơn hàng...");

        // TASK ĐỘC LẬP: Lấy tỷ giá (Dùng executor riêng)
        // Khởi tạo ở đây để nó bắt đầu chạy ngay lập tức song song với bước 1
        CompletableFuture<Double> exchangeRateTask = CompletableFuture.supplyAsync(() -> {
            System.out.println("[" + Thread.currentThread().getName() + "] 1. Đang lấy tỷ giá USD/VND...");
            try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException e) {}
            return 25000.0;
        }, executor);

        // CHUỖI PIPELINE CHÍNH
        return CompletableFuture
                .supplyAsync(() -> {
                    // Bước 1: Khởi tạo đơn hàng
                    try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) {}
                    return "ORDER_001";
                }, executor) // Dùng executor để đẩy task vào pool riêng
                .thenComposeAsync(id -> {
                    // Bước 2: Dùng ID từ bước 1 đi lấy giá (Nối chuỗi bất đồng bộ)
                    // thenComposeAsync đảm bảo việc "nối" này cũng được thực hiện bởi pool
                    System.out.println("[" + Thread.currentThread().getName() + "] 2. Đang lấy giá cho: " + id);
                    return CompletableFuture.supplyAsync(() -> 100.0, executor);
                }, executor)
                .thenCombineAsync(exchangeRateTask, (price, rate) -> {
                    // Bước 3: Đợi cả giá (price) và tỷ giá (rate) xong để gộp lại
                    System.out.println("[" + Thread.currentThread().getName() + "] 3. Đang tính giá sang VND...");
                    return price * rate;
                }, executor)
                .thenApplyAsync(totalVnd -> {
                    // Bước 4: Biến đổi dữ liệu (Cộng phí ship)
                    System.out.println("[" + Thread.currentThread().getName() + "] 4. Cộng phí giao hàng...");
                    return totalVnd + 30000;
                }, executor)
                .exceptionally(ex -> {
                    // Bước 5: Xử lý lỗi (Nếu có lỗi ở bất kỳ mắt xích nào phía trên)
                    System.err.println("[Error] Sự cố: " + ex.getMessage());
                    return 0.0;
                });

    }

    public void completableFutureWithAnyOfAndHandle() {
        CompletableFuture<String> serverA = CompletableFuture.supplyAsync(() -> {
            try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException e) {}
            return "Server A: 200$";
        });

        CompletableFuture<String> serverB = CompletableFuture.supplyAsync(() -> {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) {}
            return "Server B: 195$";
        });

        // anyOf: Lấy kết quả từ server nào phản hồi nhanh nhất
        CompletableFuture<Object> fastestResult = CompletableFuture.anyOf(serverA, serverB);

        // handle: Xử lý kết quả cuối cùng hoặc lỗi phát sinh
        fastestResult.handle((res, ex) -> {
            if (ex != null) {
                return "Không lấy được giá: " + ex.getMessage();
            }
            return "Giá tốt nhất tìm được: " + res;
        }).thenAccept(System.out::println);
    }


}
