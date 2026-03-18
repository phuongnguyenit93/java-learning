package com.example.learning.module.coordination.blockingQueue.service;

import com.example.learning.module.coordination.blockingQueue.entity.QueuePriorityTask;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class BlockingQueueService {
    public void blockingQueueExample() throws InterruptedException {
        BlockingQueue<String> orderQueue = new LinkedBlockingQueue<>(5);

        // 1. Luồng Producer
        Thread producerThread = new Thread(() -> {
            try {
                int id = 1;
                // Kiểm tra trạng thái bị ngắt trong vòng lặp
                while (!Thread.currentThread().isInterrupted()) {
                    String order = "Đơn hàng #" + id++;
                    // put() là một "blocking operation", nó sẽ tự ném InterruptedException nếu bị ngắt khi đang đợi
                    orderQueue.put(order);
                    System.out.println("[Producer] Đã gửi: " + order);
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                // Phản ứng khi bị ngắt lúc đang block (đang đợi trong put() hoặc sleep())
                System.out.println("[Producer] Nhận tín hiệu dừng! Đang dọn dẹp...");
            } finally {
                System.out.println("[Producer] Đã đóng cửa.");
            }
        }, "Producer-Thread");

        // 2. Luồng Consumer
        Thread consumerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // Sử dụng poll() với timeout thay vì take() để có thể kiểm soát việc dừng tốt hơn
                    String order = orderQueue.poll(1, TimeUnit.SECONDS);

                    if (order != null) {
                        System.out.println("    [Consumer] Đang xử lý: " + order);
                        Thread.sleep(500);
                    } else {
                        System.out.println("    [Consumer] Đang đợi đơn hàng...");
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("    [Consumer] Nhận tín hiệu dừng khi đang bận! Dừng ngay...");
            } finally {
                System.out.println("    [Consumer] Đã nghỉ việc.");
            }
        }, "Consumer-Thread");

        producerThread.start();
        consumerThread.start();

        // Giả sử hệ thống chạy trong 5 giây rồi tắt
        Thread.sleep(10000);
        System.out.println("\n=== HỆ THỐNG BẮT ĐẦU SHUTDOWN ===\n");

        // Gửi tín hiệu ngắt đến các luồng
        producerThread.interrupt();
        consumerThread.interrupt();

        // Đợi các luồng kết thúc hoàn toàn
        producerThread.join();
        consumerThread.join();

        System.out.println("\n=== HỆ THỐNG ĐÃ DỪNG HOÀN TOÀN ===");
    }

    public void synchronousQueueExample() {
        // Khởi tạo SynchronousQueue - Dung lượng bằng 0
        BlockingQueue<String> deliveryPoint = new SynchronousQueue<>();

        // 1. Luồng Shipper (Producer)
        Thread shipper = new Thread(() -> {
            try {
                String packageItem = "Điện thoại iPhone 15";
                System.out.println("[Shipper] Đang mang hàng đến: " + packageItem);

                // put() sẽ CHẶN ở đây mãi mãi cho đến khi có người gọi take()
                deliveryPoint.put(packageItem);

                System.out.println("[Shipper] Đã giao tận tay! Quay về kho.");
            } catch (InterruptedException e) {
                System.out.println("[Shipper] Bị gọi về gấp, không đợi nữa.");
            }
        });

        // 2. Luồng Khách hàng (Consumer)
        Thread customer = new Thread(() -> {
            try {
                System.out.println("    [Khách hàng] Đang làm việc khác...");
                Thread.sleep(3000); // Khách bận 3 giây mới ra cửa

                System.out.println("    [Khách hàng] Ra nhận hàng...");

                // take() lấy hàng trực tiếp từ tay shipper
                String item = deliveryPoint.take();

                System.out.println("    [Khách hàng] Đã nhận: " + item);
            } catch (InterruptedException e) {
                System.out.println("    [Khách hàng] Bị ngắt quãng.");
            }
        });

        shipper.start();
        customer.start();
    }

    public void arrayBlockQueue() {
        // Khởi tạo hàng đợi với dung lượng cố định là 2
        BlockingQueue<String> coffeeQueue = new ArrayBlockingQueue<>(2,true);

        // Luồng Barista (Người tiêu dùng - Consumer)
        Thread barista = new Thread(() -> {
            try {
                while (true) {
                    System.out.println("Barista: Đang đợi đơn hàng...");
                    String order = coffeeQueue.take(); // Sẽ đợi (block) nếu hàng đợi trống
                    System.out.println("Barista: Đang pha " + order);
                    Thread.sleep(3000); // Pha mất 3 giây
                    System.out.println("Barista: Đã pha xong " + order);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Luồng Cashier (Người sản xuất - Producer)
        Thread cashier = new Thread(() -> {
            try {
                String[] orders = {"Latte", "Cappuccino", "Espresso", "Americano"};
                for (String o : orders) {
                    System.out.println("Cashier: Đang đẩy " + o + " vào quầy...");

                    if (coffeeQueue.offer(o,1,TimeUnit.SECONDS)) {
                        System.out.println("Cashier: Đã đẩy thành công " + o);
                    } else {
                        System.out.println("Cashier: Quầy pha chế đang đầy . Đơn " + o + " thử lại sau");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        barista.start();
        cashier.start();
    }

    public void priorityQueueExample() throws InterruptedException {
        PriorityBlockingQueue<QueuePriorityTask> queue = new PriorityBlockingQueue<>();

        // Producer: Đẩy các thông báo vào không theo thứ tự
        System.out.println("--- Đang đẩy task vào hàng đợi ---");
        queue.put(new QueuePriorityTask("Khuyến mãi mua 1 tặng 1", 3)); // Thấp
        queue.put(new QueuePriorityTask("Mã OTP đăng nhập: 1234", 1));   // Cao nhất
        queue.put(new QueuePriorityTask("Đơn hàng của bạn đã giao", 2)); // Trung bình

        // Consumer: Lấy task ra xử lý
        System.out.println("--- Bắt đầu xử lý task ---");
        while (!queue.isEmpty()) {
            // Dù đẩy vào cuối, nhưng OTP sẽ luôn được take() ra đầu tiên
            QueuePriorityTask task = queue.take();
            System.out.println("Đang thực hiện: " + task);
            Thread.sleep(500);
        }
    }
}
